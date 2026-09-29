#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    ivec2 size = textureSize(InSampler, 0);
    vec2 pixel = texCoord * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(pixel));
    vec2 fraction = fract(pixel);
    float centerDepth = texture(DistanceSampler, texCoord).r;
    float scale = max(0.2, centerDepth * 0.02);
    float total = 0.0;
    float closest = 1e30;
    vec3 color = vec3(0.0);
    vec3 nearestColor = vec3(0.0);
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            ivec2 samplePixel = clamp(base + ivec2(x, y), ivec2(0), size - 1);
            vec2 sampleUv = (vec2(samplePixel) + 0.5) / vec2(size);
            float sampleDepth = texture(DistanceSampler, sampleUv).r;
            float difference = abs(centerDepth - sampleDepth);
            vec3 sampleColor = texelFetch(InSampler, samplePixel, 0).rgb;
            if (difference < closest) {
                closest = difference;
                nearestColor = sampleColor;
            }
            float spatial = (x == 0 ? 1.0 - fraction.x : fraction.x)
                    * (y == 0 ? 1.0 - fraction.y : fraction.y);
            float weight = spatial * exp(-difference / scale);
            color += sampleColor * weight;
            total += weight;
        }
    }
    fragColor = vec4(total > 1e-5 ? color / total : nearestColor, 1.0);
}
