#version 330
uniform sampler2D InSampler;
layout(std140) uniform LensSettings { vec4 Lens; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 center = Lens.zw;
    vec2 offset = texCoord - center;
    float radius = dot(offset, offset);
    vec2 warped = center + offset * (1.0 + Lens.x * radius);
    vec2 separation = offset * (Lens.y * radius * 0.015);
    vec2 r = clamp(warped + separation, vec2(0.0), vec2(1.0));
    vec2 g = clamp(warped, vec2(0.0), vec2(1.0));
    vec2 b = clamp(warped - separation, vec2(0.0), vec2(1.0));
    fragColor = vec4(texture(InSampler, r).r, texture(InSampler, g).g,
                     texture(InSampler, b).b, texture(InSampler, g).a);
}
