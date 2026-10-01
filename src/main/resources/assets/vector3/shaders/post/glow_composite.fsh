#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // The old two-level blur summed two layers; doubling keeps existing strengths looking the same.
    fragColor = vec4(texture(InSampler, texCoord).rgb * 2.0, 1.0);
}
