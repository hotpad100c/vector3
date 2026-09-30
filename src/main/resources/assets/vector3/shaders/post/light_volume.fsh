#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D DistanceSampler;
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

float hashNoise(vec3 cell) {
    return fract(sin(dot(cell, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float fogNoise(vec3 position) {
    vec3 cell = floor(position);
    vec3 fraction = fract(position);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    float x00 = mix(hashNoise(cell), hashNoise(cell + vec3(1.0, 0.0, 0.0)), fraction.x);
    float x10 = mix(hashNoise(cell + vec3(0.0, 1.0, 0.0)), hashNoise(cell + vec3(1.0, 1.0, 0.0)), fraction.x);
    float x01 = mix(hashNoise(cell + vec3(0.0, 0.0, 1.0)), hashNoise(cell + vec3(1.0, 0.0, 1.0)), fraction.x);
    float x11 = mix(hashNoise(cell + vec3(0.0, 1.0, 1.0)), hashNoise(cell + vec3(1.0, 1.0, 1.0)), fraction.x);
    return mix(mix(x00, x10, fraction.y), mix(x01, x11, fraction.y), fraction.z);
}

bool areaInterval(vec3 ray, vec3 center, float radius, out float start, out float end) {
    vec3 origin = vec3(dot(-center, AreaRight.xyz), dot(-center, AreaUp.xyz),
            dot(-center, LightDirectionType.xyz));
    vec3 direction = vec3(dot(ray, AreaRight.xyz), dot(ray, AreaUp.xyz),
            dot(ray, LightDirectionType.xyz));
    vec3 lower = vec3(-AreaCone.x * 0.5 - radius, -AreaCone.y * 0.5 - radius, 0.0);
    vec3 upper = vec3(AreaCone.x * 0.5 + radius, AreaCone.y * 0.5 + radius, AreaRight.w);
    start = 0.0;
    end = 50000.0;
    for (int axis = 0; axis < 3; axis++) {
        if (abs(direction[axis]) < 1e-5) {
            if (origin[axis] < lower[axis] || origin[axis] > upper[axis]) return false;
        } else {
            float a = (lower[axis] - origin[axis]) / direction[axis];
            float b = (upper[axis] - origin[axis]) / direction[axis];
            start = max(start, min(a, b));
            end = min(end, max(a, b));
        }
    }
    return end > start;
}

float screenOcclusion(vec3 point, vec3 emitter) {
    float phase = hashNoise(vec3(gl_FragCoord.xy, point.z));
    vec2 uvJitter = (vec2(phase, fract(phase * 1.618)) - 0.5) * Projection.zw * 1.5;
    float coverage = 0.0;
    for (int i = 0; i < 8; i++) {
        vec3 probe = mix(point, emitter, (float(i) + phase) / 8.0);
        if (probe.z >= -0.05) continue;
        vec4 clip = ProjectionMatrix * vec4(probe, 1.0);
        if (clip.w <= 0.0) continue;
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5 + uvJitter;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) continue;
        float observed = texture(DistanceSampler, uv).r;
        if (observed < 0.01 || observed >= 50000.0) continue;
        float bias = max(0.12, -probe.z * 0.004);
        float separation = -probe.z - observed;
        coverage += smoothstep(bias, bias + max(0.15, -probe.z * 0.01), separation);
    }
    return 1.0 - exp(-coverage * 1.3);
}

void main() {
    vec4 rayPoint = InverseProjectionMatrix * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    vec3 ray = normalize(rayPoint.xyz / rayPoint.w);
    vec3 center = LightPositionRadius.xyz;
    float radius = LightPositionRadius.w;
    float start, end;
    if (LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5) {
        if (!areaInterval(ray, center, radius, start, end)) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    } else {
        float bounds = radius;
        vec3 boundsCenter = center;
        if (LightDirectionType.w > 1.5) {
            float tangent = sqrt(max(1.0 - AreaCone.w * AreaCone.w, 0.0)) / max(AreaCone.w, 0.017);
            boundsCenter += LightDirectionType.xyz * radius * 0.5;
            bounds = length(vec2(radius * 0.5, radius * tangent));
        }
        float projected = dot(ray, boundsCenter);
        float discriminant = projected * projected - (dot(boundsCenter, boundsCenter) - bounds * bounds);
        if (discriminant <= 0.0) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        float reach = sqrt(discriminant);
        start = max(0.0, projected - reach);
        end = projected + reach;
    }
    float depth = texture(DistanceSampler, texCoord).r;
    end = min(end, depth / max(-ray.z, 1e-4));
    if (end <= start) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    int steps = min(64, (LightDirectionType.w > 0.5 ? 48 : 24)
            + int(clamp(Effects.w / 8.0, 0.0, 16.0)));
    float stepLength = (end - start) / float(steps);
    float scattering = 0.0;
    float jitter = hashNoise(vec3(gl_FragCoord.xy, 0.0));
    float occlusionAnchors[8];
    for (int anchor = 0; anchor < 8; anchor++) {
        occlusionAnchors[anchor] = 0.0;
        if (Effects.y <= 0.001) continue;
        vec3 samplePoint = ray * mix(start, end, (float(anchor) + 0.5) / 8.0);
        vec3 emitter = center;
        if (LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5) {
            vec3 displacement = samplePoint - center;
            emitter += AreaRight.xyz * clamp(dot(displacement, AreaRight.xyz),
                    -AreaCone.x * 0.5, AreaCone.x * 0.5);
            emitter += AreaUp.xyz * clamp(dot(displacement, AreaUp.xyz),
                    -AreaCone.y * 0.5, AreaCone.y * 0.5);
        }
        occlusionAnchors[anchor] = screenOcclusion(samplePoint, emitter);
    }
    for (int i = 0; i < 64; i++) {
        if (i >= steps) break;
        vec3 point = ray * (start + (float(i) + jitter) * stepLength);
        vec3 emitter = center;
        float shape = 1.0;
        float radial;
        if (LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5) {
            vec3 displacement = point - center;
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
            shape *= smoothstep(0.0, max(0.1, reach * 0.025), axial)
                    * (1.0 - smoothstep(reach * 0.85, reach, axial));
            radial = 1.0 / (1.0 + 2.0 * (axial / reach) * (axial / reach));
        } else if (LightDirectionType.w > 1.5) {
            vec3 toPoint = point - center;
            float axial = dot(LightDirectionType.xyz, toPoint);
            float lateral = length(toPoint - LightDirectionType.xyz * axial);
            float outerTangent = sqrt(max(1.0 - AreaCone.w * AreaCone.w, 0.0))
                    / max(AreaCone.w, 0.017);
            float innerTangent = sqrt(max(1.0 - AreaCone.z * AreaCone.z, 0.0))
                    / max(AreaCone.z, 0.017);
            float outerWidth = max(axial, 0.0) * outerTangent;
            float innerWidth = max(axial, 0.0) * innerTangent;
            float feather = max(outerWidth - innerWidth, 0.01);
            shape = 1.0 - smoothstep(outerWidth - feather, outerWidth, lateral);
            shape *= smoothstep(0.0, max(radius * 0.025, 0.05), axial);
            shape *= 1.0 - smoothstep(radius * 0.88, radius, axial);
            radial = 1.0 / (1.0 + 2.0 * (axial / radius) * (axial / radius));
        } else {
            radial = max(0.0, 1.0 - length(point - center) / radius);
        }
        if (shape <= 0.0 || radial <= 0.0) continue;
        vec3 displacement = point - center;
        vec3 local = vec3(dot(displacement, AreaRight.xyz),
                dot(displacement, AreaUp.xyz), dot(displacement, LightDirectionType.xyz));
        float variation = 0.85 + 0.3 * fogNoise(local * 2.0);
        float anchorPosition = clamp((float(i) + jitter) / float(steps) * 8.0 - 0.5, 0.0, 7.0);
        int anchor = min(int(floor(anchorPosition)), 6);
        float occlusion = mix(occlusionAnchors[anchor], occlusionAnchors[anchor + 1],
                anchorPosition - float(anchor));
        float visibility = 1.0 - Effects.y * occlusion;
        scattering += radial * radial * shape * variation * visibility;
    }
    float lengthScale = LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5
            ? max(AreaRight.w, 0.1) : max(radius, 0.1);
    scattering *= stepLength / lengthScale;
    fragColor = vec4(LightColorIntensity.rgb * LightColorIntensity.a * Effects.x
            * scattering * 0.55 * (1.0 + Effects.z * 0.3), 1.0);
}
