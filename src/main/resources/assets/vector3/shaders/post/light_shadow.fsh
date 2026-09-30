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

float noise(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

float blocked(vec3 start, vec3 target, float jitter) {
    vec3 ray = target - start;
    float bias = max(0.12, -start.z * 0.003);
    float occlusion = 0.0;
    for (int i = 0; i < 24; i++) {
        float t = (float(i) + jitter) / 24.0;
        vec3 point = start + ray * t;
        if (point.z >= -0.01) break;
        vec4 clip = ProjectionMatrix * vec4(point, 1.0);
        if (clip.w <= 0.0) continue;
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) continue;
        float observed = texture(DistanceSampler, uv).r;
        if (observed > 0.01 && observed < 50000.0) {
            float thickness = max(0.1, -point.z * 0.01);
            occlusion = max(occlusion, smoothstep(bias, bias + thickness, -point.z - observed));
            if (occlusion >= 0.999) break;
        }
    }
    return occlusion;
}

void main() {
    float depth = texture(DistanceSampler, texCoord).r;
    if (depth <= 0.01 || depth >= 50000.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec4 rayPoint = InverseProjectionMatrix * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    vec3 ray = rayPoint.xyz / rayPoint.w;
    vec3 surface = ray * (depth / -ray.z);
    float reach = LightPositionRadius.w * 2.5;
    if (LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5)
        reach = length(vec2(AreaRight.w, reach + length(AreaCone.xy) * 0.5));
    if (length(LightPositionRadius.xyz - surface) > reach) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 start = surface + normalize(-surface) * max(0.12, depth * 0.003);
    vec3 center = LightPositionRadius.xyz;
    float jitter = noise(gl_FragCoord.xy);
    float occlusion = blocked(start, center, jitter);
    if (LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5) {
        vec3 dx = AreaRight.xyz * AreaCone.x * 0.38;
        vec3 dy = AreaUp.xyz * AreaCone.y * 0.38;
        occlusion += blocked(start, center + dx + dy, jitter);
        occlusion += blocked(start, center + dx - dy, jitter);
        occlusion += blocked(start, center - dx + dy, jitter);
        occlusion += blocked(start, center - dx - dy, jitter);
    } else {
        float spread = clamp(LightPositionRadius.w * 0.04, 0.06, 0.4);
        occlusion += blocked(start, center + vec3(spread, 0.0, 0.0), jitter);
        occlusion += blocked(start, center - vec3(spread, 0.0, 0.0), jitter);
        occlusion += blocked(start, center + vec3(0.0, spread, 0.0), jitter);
        occlusion += blocked(start, center - vec3(0.0, spread, 0.0), jitter);
    }
    fragColor = vec4(occlusion * 0.2, 0.0, 0.0, 1.0);
}
