#version 330
uniform sampler2D LumSampler;
uniform sampler2D PrevSampler;
layout(std140) uniform ExposureSettings { vec4 Settings; vec4 Detail; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    float lum = texture(LumSampler, vec2(0.5)).r;
    if (isnan(lum) || isinf(lum)) lum = Detail.x;
    float target = clamp(Detail.x / max(lum, 0.001) * exp2(Settings.w), exp2(Settings.y), exp2(Settings.z));
    float previous = Detail.y > 0.5 ? texture(PrevSampler, vec2(0.5)).r : target;
    if (isnan(previous) || isinf(previous) || previous <= 0.0) previous = target;
    // Bright scenes need immediate exposure reduction; recovery in darkness can remain gradual.
    float blend = target < previous ? 1.0 : Settings.x;
    fragColor = vec4(mix(previous, target, blend), 0.0, 0.0, 1.0);
}
