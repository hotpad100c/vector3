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

void main() {
    vec4 rayPoint = InverseProjectionMatrix * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    vec3 ray = normalize(rayPoint.xyz / rayPoint.w);
    vec3 center = LightPositionRadius.xyz;
    float radius = LightPositionRadius.w;
    float bounds = radius;
    if (LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5)
        bounds = length(vec2(AreaRight.w, radius + length(AreaCone.xy) * 0.5));
    float projected = dot(ray, center);
    float discriminant = projected * projected - (dot(center, center) - bounds * bounds);
    if (discriminant <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    float reach = sqrt(discriminant);
    float start = max(0.0, projected - reach);
    float depth = texture(DistanceSampler, texCoord).r;
    float end = min(projected + reach, depth / max(-ray.z, 1e-4));
    if (end <= start) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    int steps = LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5 ? 24 : 12;
    float stepLength = (end - start) / float(steps);
    float scattering = 0.0;
    for (int i = 0; i < 24; i++) {
        if (i >= steps) break;
        vec3 point = ray * (start + (float(i) + 0.5) * stepLength);
        vec3 emitter = center;
        float shape = 1.0;
        float radial;
        if (LightDirectionType.w > 0.5 && LightDirectionType.w < 1.5) {
            vec3 displacement = point - center;
            emitter += AreaRight.xyz * clamp(dot(displacement, AreaRight.xyz),
                    -AreaCone.x * 0.5, AreaCone.x * 0.5);
            emitter += AreaUp.xyz * clamp(dot(displacement, AreaUp.xyz),
                    -AreaCone.y * 0.5, AreaCone.y * 0.5);
            vec3 toPoint = point - emitter;
            float axial = dot(LightDirectionType.xyz, toPoint);
            radial = max(0.0, 1.0 - length(toPoint - LightDirectionType.xyz * axial) / radius);
            shape = smoothstep(-0.05, 0.15,
                    dot(LightDirectionType.xyz, toPoint / max(length(toPoint), 0.001)));
            shape *= 1.0 - smoothstep(AreaRight.w * 0.75, AreaRight.w, axial);
        } else if (LightDirectionType.w > 1.5) {
            vec3 toPoint = point - center;
            float cone = dot(LightDirectionType.xyz, toPoint / max(length(toPoint), 0.001));
            shape = clamp((cone - AreaCone.w) / max(AreaCone.z - AreaCone.w, 0.001), 0.0, 1.0);
            shape = shape * shape * (3.0 - 2.0 * shape);
            radial = max(0.0, 1.0 - length(point - center) / radius);
        } else {
            radial = max(0.0, 1.0 - length(point - center) / radius);
        }
        scattering += radial * radial * shape;
    }
    scattering *= stepLength / max(radius, 0.1);
    fragColor = vec4(LightColorIntensity.rgb * LightColorIntensity.a * Effects.x
            * scattering * 0.55, 1.0);
}
