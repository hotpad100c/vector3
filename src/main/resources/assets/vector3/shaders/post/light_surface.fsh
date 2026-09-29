#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
uniform sampler2D ShadowSampler;
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

void main() {
    float depth = texture(DistanceSampler, texCoord).r;
    if (depth <= 0.01 || depth >= 50000.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec4 rayPoint = InverseProjectionMatrix * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    vec3 ray = rayPoint.xyz / rayPoint.w;
    vec3 surface = ray * (depth / -ray.z);
    float radius = max(LightPositionRadius.w, 0.001);
    float normalizedDistance = length(LightPositionRadius.xyz - surface) / radius;
    float attenuation = exp(-3.0 * normalizedDistance * normalizedDistance);
    float visibility = Effects.y > 0.001 ? 1.0 - Effects.y * shadowOcclusion(depth) : 1.0;
    vec3 scene = texture(InSampler, texCoord).rgb;
    vec3 albedo = mix(vec3(0.35), scene, 0.65);
    fragColor = vec4(LightColorIntensity.rgb * LightColorIntensity.a * attenuation
            * visibility * albedo, 1.0);
}
