#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform PaniniSettings { vec4 Panini; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 centered = (texCoord - 0.5) * vec2(Panini.z, 1.0) * 2.0;
    float radius = length(centered);
    float factor = mix(1.0, (1.0 + Panini.x) / (Panini.x + sqrt(1.0 + radius * radius)), Panini.x);
    vec2 uv = 0.5 + centered * factor * mix(1.0, 1.0 + Panini.x * 0.3, Panini.y)
            / vec2(Panini.z, 1.0) * 0.5;
    fragColor = texture(InSampler, clamp(uv, vec2(0.0), vec2(1.0)));
}
