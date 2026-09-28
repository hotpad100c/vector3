#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 t = 1.0 / vec2(textureSize(InSampler, 0));
    fragColor = 0.25 * (texture(InSampler, texCoord + vec2(-t.x, -t.y)) + texture(InSampler, texCoord + vec2(t.x, -t.y))
            + texture(InSampler, texCoord + vec2(-t.x, t.y)) + texture(InSampler, texCoord + vec2(t.x, t.y)));
}
