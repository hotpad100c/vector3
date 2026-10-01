#version 330
uniform sampler2D InSampler;
layout(std140) uniform GodRaysComposite {
    vec4 Color; // tint times intensity, faded by daylight and how far off-screen the sun is
};
in vec2 texCoord;
out vec4 fragColor;
void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb * Color.rgb, 1.0);
}
