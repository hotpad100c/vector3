#version 330
uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

void main() {
    vec4 material = texture(Sampler0, texCoord) * tint;
    if (material.a < 0.1) discard;
    fragColor = vec4(material.rgb, 1.0);
}
