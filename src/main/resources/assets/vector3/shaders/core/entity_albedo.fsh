#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 tint;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 material = texture(Sampler0, texCoord) * tint;
    if (material.a < 0.1) discard;
    fragColor = vec4(material.rgb, 1.0);
}
