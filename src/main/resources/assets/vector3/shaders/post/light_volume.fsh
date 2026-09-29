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
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 rayPoint = InverseProjectionMatrix * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    vec3 ray = normalize(rayPoint.xyz / rayPoint.w);
    vec3 center = LightPositionRadius.xyz;
    float radius = LightPositionRadius.w;
    float projected = dot(ray, center);
    float discriminant = projected * projected - (dot(center, center) - radius * radius);
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
    float stepLength = (end - start) / 12.0;
    float scattering = 0.0;
    for (int i = 0; i < 12; i++) {
        vec3 point = ray * (start + (float(i) + 0.5) * stepLength);
        float radial = max(0.0, 1.0 - length(point - center) / radius);
        scattering += radial * radial;
    }
    scattering *= stepLength / max(radius, 0.1);
    fragColor = vec4(LightColorIntensity.rgb * LightColorIntensity.a * Effects.x
            * scattering * 0.55, 1.0);
}
