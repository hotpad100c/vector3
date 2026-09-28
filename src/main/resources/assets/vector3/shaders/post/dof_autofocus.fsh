#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D DistanceSampler;
uniform sampler2D PrevSampler;
layout(std140) uniform AutofocusSettings {
    vec4 Blend; // x: how far to move towards the centre distance this frame
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    float target = log2(max(texture(DistanceSampler, vec2(0.5)).r, 0.05));
    float previous = log2(max(texture(PrevSampler, vec2(0.5)).r, 0.05));
    fragColor = vec4(exp2(mix(previous, target, clamp(Blend.x, 0.0, 1.0))), 0.0, 0.0, 1.0);
}
