#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    // The old two-level blur summed two layers; doubling keeps existing strengths looking the same.
    fragColor = vec4(texture(InSampler, texCoord).rgb * 2.0, 1.0);
}
