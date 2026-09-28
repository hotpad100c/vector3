#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D LumSampler;
uniform sampler2D PrevSampler;
layout(std140) uniform ExposureSettings { vec4 Settings; vec4 Detail; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    float lum = texture(LumSampler, vec2(0.5)).r;
    float target = clamp(Detail.x / max(lum, 0.001) * exp2(Settings.w), exp2(Settings.y), exp2(Settings.z));
    float previous = Detail.y > 0.5 ? texture(PrevSampler, vec2(0.5)).r : target;
    fragColor = vec4(mix(previous, target, Settings.x), 0.0, 0.0, 1.0);
}
