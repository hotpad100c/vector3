#version 330
uniform sampler2D InSampler;
layout(std140) uniform BloomPrefilter { vec4 Threshold; vec4 Sample; };
in vec2 texCoord;
out vec4 fragColor;

vec3 sampleScene(vec2 uv) { return min(texture(InSampler, uv).rgb, vec3(Threshold.z)); }
void main() {
    vec3 colour = sampleScene(texCoord);
    if (Sample.z > 0.5) {
        vec2 d = Sample.xy;
        vec3 a = sampleScene(texCoord + vec2(d.x, d.y));
        vec3 b = sampleScene(texCoord + vec2(-d.x, d.y));
        vec3 c = sampleScene(texCoord + vec2(d.x, -d.y));
        vec3 e = sampleScene(texCoord - d);
        colour = (colour * 4.0 + a + b + c + e) / 8.0;
        if (Threshold.w > 0.5) colour = min(colour, max(max(a, b), max(c, e)));
    }
    float brightness = max(max(colour.r, colour.g), colour.b);
    float knee = max(Threshold.x * Threshold.y, 1e-4);
    float soft = clamp(brightness - Threshold.x + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float contribution = max(brightness - Threshold.x, soft) / max(brightness, 1e-4);
    fragColor = vec4(colour * contribution, 1.0);
}
