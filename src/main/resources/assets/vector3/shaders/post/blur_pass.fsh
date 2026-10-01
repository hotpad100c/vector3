#version 330
uniform sampler2D InSampler;
layout(std140) uniform BlurPass {
    vec4 Pass; // texel step along the blur axis, sigma, taps on each side
};
in vec2 texCoord;
out vec4 fragColor;

// One axis of a Gaussian; running it along x and then y gives the full 2D blur.
void main() {
    int taps = int(Pass.w + 0.5);
    float inverseSigma = 1.0 / max(Pass.z, 0.001);
    vec3 sum = texture(InSampler, texCoord).rgb;
    float total = 1.0;
    for (int i = 1; i <= 24; i++) {
        if (i > taps) break;
        float weight = exp(-0.5 * float(i * i) * inverseSigma * inverseSigma);
        vec2 offset = Pass.xy * float(i);
        sum += weight * (texture(InSampler, texCoord + offset).rgb + texture(InSampler, texCoord - offset).rgb);
        total += 2.0 * weight;
    }
    fragColor = vec4(sum / total, 1.0);
}
