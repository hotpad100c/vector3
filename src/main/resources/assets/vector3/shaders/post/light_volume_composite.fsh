#version 330
uniform sampler2D InSampler;
uniform sampler2D SceneSampler;
uniform sampler2D DistanceSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    ivec2 size = textureSize(InSampler, 0);
    vec2 pixel = texCoord * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(pixel + 0.5));
    float centerDepth = texture(DistanceSampler, texCoord).r;
    float scale = max(0.2, centerDepth * 0.02);
    float total = 0.0;
    float closest = 1e30;
    vec3 color = vec3(0.0);
    vec3 nearestColor = vec3(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 samplePixel = clamp(base + ivec2(x, y), ivec2(0), size - 1);
            vec2 sampleUv = (vec2(samplePixel) + 0.5) / vec2(size);
            float sampleDepth = texture(DistanceSampler, sampleUv).r;
            float difference = abs(centerDepth - sampleDepth);
            vec3 sampleColor = texelFetch(InSampler, samplePixel, 0).rgb;
            if (difference < closest) {
                closest = difference;
                nearestColor = sampleColor;
            }
            vec2 delta = vec2(samplePixel) - pixel;
            float spatial = exp(-0.5 * dot(delta, delta));
            float weight = spatial * exp(-difference / scale);
            color += sampleColor * weight;
            total += weight;
        }
    }
    vec3 light = max(total > 1e-5 ? color / total : nearestColor, vec3(0.0));
    vec4 scene = texture(SceneSampler, texCoord);
    vec3 result = scene.rgb + (1.0 - scene.rgb) * (1.0 - exp(-light));
    fragColor = vec4(clamp(result, 0.0, 1.0), scene.a);
}
