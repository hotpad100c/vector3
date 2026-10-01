#version 330

layout(std140) uniform FadeColor {
    vec4 Color;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = Color;
}
