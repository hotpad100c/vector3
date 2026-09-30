#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D AlbedoDepthSampler;
uniform sampler2D DistanceSampler;
uniform sampler2D ShadowSampler;
layout(std140) uniform LightSettings {
    vec4 LightPositionRadius;
    vec4 LightColorIntensity;
    vec4 Projection;
    vec4 Effects;
    mat4 ProjectionMatrix;
    mat4 InverseProjectionMatrix;
    vec4 LightDirectionType;
    vec4 AreaCone;
    vec4 AreaRight;
    vec4 AreaUp;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 viewPosition(vec2 uv, float depth) {
    vec4 point = InverseProjectionMatrix * vec4(uv * 2.0 - 1.0, 0.5, 1.0);
    vec3 ray = point.xyz / point.w;
    return ray * (depth / -ray.z);
}

vec3 surfaceNormal(vec3 surface, float depth) {
    ivec2 size = textureSize(DistanceSampler, 0);
    ivec2 pixel = clamp(ivec2(texCoord * vec2(size)), ivec2(0), size - 1);
    ivec2 left = max(pixel - ivec2(1, 0), ivec2(0));
    ivec2 right = min(pixel + ivec2(1, 0), size - 1);
    ivec2 down = max(pixel - ivec2(0, 1), ivec2(0));
    ivec2 up = min(pixel + ivec2(0, 1), size - 1);
    float dl = texelFetch(DistanceSampler, left, 0).r;
    float dr = texelFetch(DistanceSampler, right, 0).r;
    float dd = texelFetch(DistanceSampler, down, 0).r;
    float du = texelFetch(DistanceSampler, up, 0).r;
    float el = dl > 0.01 && dl < 50000.0 ? abs(dl - depth) : 1e20;
    float er = dr > 0.01 && dr < 50000.0 ? abs(dr - depth) : 1e20;
    float ed = dd > 0.01 && dd < 50000.0 ? abs(dd - depth) : 1e20;
    float eu = du > 0.01 && du < 50000.0 ? abs(du - depth) : 1e20;
    vec3 facing = normalize(-surface);
    if (min(el, er) > depth * 0.08 || min(ed, eu) > depth * 0.08)
        return facing;
    vec3 dx = er <= el
            ? viewPosition((vec2(right) + 0.5) / vec2(size), dr) - surface
            : surface - viewPosition((vec2(left) + 0.5) / vec2(size), dl);
    vec3 dy = eu <= ed
            ? viewPosition((vec2(up) + 0.5) / vec2(size), du) - surface
            : surface - viewPosition((vec2(down) + 0.5) / vec2(size), dd);
    vec3 normal = cross(dx, dy);
    if (dot(normal, normal) < 1e-12) return facing;
    normal = normalize(normal);
    if (dot(normal, facing) < 0.0) normal = -normal;
    float edge = max(min(el, er), min(ed, eu)) / max(depth, 0.25);
    float confidence = 1.0 - smoothstep(0.015, 0.08, edge);
    return normalize(mix(facing, normal, confidence));
}

float shadowOcclusion(float depth) {
    ivec2 size = textureSize(ShadowSampler, 0);
    vec2 pixel = texCoord * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(pixel));
    vec2 fraction = fract(pixel);
    float scale = max(0.2, depth * 0.02);
    float sum = 0.0;
    float total = 0.0;
    float closest = 1e30;
    float nearest = 0.0;
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            ivec2 samplePixel = clamp(base + ivec2(x, y), ivec2(0), size - 1);
            vec2 sampleUv = (vec2(samplePixel) + 0.5) / vec2(size);
            float difference = abs(depth - texture(DistanceSampler, sampleUv).r);
            float occlusion = texelFetch(ShadowSampler, samplePixel, 0).r;
            if (difference < closest) {
                closest = difference;
                nearest = occlusion;
            }
            float spatial = (x == 0 ? 1.0 - fraction.x : fraction.x)
                    * (y == 0 ? 1.0 - fraction.y : fraction.y);
            float weight = spatial * exp(-difference / scale);
            sum += occlusion * weight;
            total += weight;
        }
    }
    return total > 1e-5 ? sum / total : nearest;
}

// Smooth only the light's material response. The original scene remains sharp in the composite.
vec3 surfaceAlbedo(float depth) {
    if (AreaUp.w > 0.5) {
        vec4 material = texture(AlbedoSampler, texCoord);
        if (material.a > 0.5) {
            float rawDepth = texture(AlbedoDepthSampler, texCoord).r;
            float ndc = AreaUp.w > 1.5 ? rawDepth : rawDepth * 2.0 - 1.0;
            float denominator = ndc * ProjectionMatrix[2][3] - ProjectionMatrix[2][2];
            float materialDepth = abs(denominator) < 1e-7 ? 60000.0
                    : (ndc * ProjectionMatrix[3][3] - ProjectionMatrix[3][2]) / denominator;
            if (abs(materialDepth - depth) < max(0.05, depth * 0.01))
                return material.rgb;
        }
    }
    vec3 center = texture(InSampler, texCoord).rgb;
    vec3 sum = center;
    float total = 1.0;
    vec2 stepUv = Projection.zw * 12.0;
    for (int i = 0; i < 4; i++) {
        vec2 offset = i == 0 ? vec2(stepUv.x, 0.0)
                : i == 1 ? vec2(-stepUv.x, 0.0)
                : i == 2 ? vec2(0.0, stepUv.y) : vec2(0.0, -stepUv.y);
        vec2 uv = clamp(texCoord + offset, vec2(0.0), vec2(1.0));
        float sampleDepth = texture(DistanceSampler, uv).r;
        vec3 sampleColor = texture(InSampler, uv).rgb;
        vec3 colorDifference = sampleColor - center;
        float weight = sampleDepth > 0.01 && sampleDepth < 50000.0
                ? exp(-abs(sampleDepth - depth) / max(0.08, depth * 0.015)
                        - 12.0 * dot(colorDifference, colorDifference)) : 0.0;
        sum += sampleColor * weight;
        total += weight;
    }
    // Keep the source texel's dark and colored material boundaries, even after smoothing.
    return mix(center, sum / total, 0.6) * smoothstep(0.0, 0.1,
            max(max(center.r, center.g), center.b));
}

void main() {
    float depth = texture(DistanceSampler, texCoord).r;
    if (depth <= 0.01 || depth >= 50000.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 surface = viewPosition(texCoord, depth);
    float radius = max(LightPositionRadius.w, 0.001);
    vec3 emitter = LightPositionRadius.xyz;
    float shape = 1.0;
    float normalizedDistance = length(emitter - surface) / radius;
    if (LightDirectionType.w < 1.5 && LightDirectionType.w > 0.5) {
        vec3 displacement = surface - emitter;
        emitter += AreaRight.xyz * clamp(dot(displacement, AreaRight.xyz),
                -AreaCone.x * 0.5, AreaCone.x * 0.5);
        emitter += AreaUp.xyz * clamp(dot(displacement, AreaUp.xyz),
                -AreaCone.y * 0.5, AreaCone.y * 0.5);
        float reach = max(AreaRight.w, 0.1);
        float axial = dot(LightDirectionType.xyz, displacement);
        float spread = radius * clamp(axial / reach, 0.0, 1.0);
        float halfWidth = AreaCone.x * 0.5 + spread;
        float halfHeight = AreaCone.y * 0.5 + spread;
        float feather = max(0.08, spread * 0.12);
        shape = (1.0 - smoothstep(halfWidth - feather, halfWidth + feather,
                abs(dot(displacement, AreaRight.xyz))))
                * (1.0 - smoothstep(halfHeight - feather, halfHeight + feather,
                abs(dot(displacement, AreaUp.xyz))));
        shape *= smoothstep(-0.04, 0.12, axial)
                * (1.0 - smoothstep(reach * 0.85, reach, axial));
        vec2 lateral = vec2(dot(displacement, AreaRight.xyz) / max(halfWidth, 0.1),
                dot(displacement, AreaUp.xyz) / max(halfHeight, 0.1));
        normalizedDistance = length(vec3(max(axial, 0.0) / reach, lateral * 0.35));
    } else if (LightDirectionType.w > 1.5) {
        vec3 toSurface = surface - emitter;
        float cone = dot(normalize(LightDirectionType.xyz), toSurface / max(length(toSurface), 0.001));
        shape = clamp((cone - AreaCone.w) / max(AreaCone.z - AreaCone.w, 0.001), 0.0, 1.0);
        shape = shape * shape * (3.0 - 2.0 * shape);
    }
    float attenuation = exp(-3.0 * normalizedDistance * normalizedDistance);
    vec3 toLight = emitter - surface;
    float diffuse = max(dot(surfaceNormal(surface, depth),
            toLight / max(length(toLight), 0.001)), 0.0);
    diffuse = 0.08 + 0.92 * diffuse;
    float visibility = Effects.y > 0.001 ? 1.0 - Effects.y * shadowOcclusion(depth) : 1.0;
    vec3 albedo = surfaceAlbedo(depth);
    fragColor = vec4(LightColorIntensity.rgb * LightColorIntensity.a * attenuation * shape
            * diffuse * visibility * albedo, 1.0);
}
