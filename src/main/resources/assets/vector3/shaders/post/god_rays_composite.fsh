#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform GodRaysComposite {
    vec4 Color; // tint times intensity, faded by daylight and how far off-screen the sun is
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb * Color.rgb, 1.0);
}
