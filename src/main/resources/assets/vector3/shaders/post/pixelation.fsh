#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform PixelationSettings { vec4 Pixel; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 block = max(vec2(Pixel.x), vec2(1.0));
    vec2 uv = (floor(texCoord * Pixel.zw / block) + 0.5) * block / Pixel.zw;
    vec4 scene = texture(InSampler, clamp(uv, vec2(0.0), vec2(1.0)));
    float levels = max(Pixel.y - 1.0, 1.0);
    fragColor = vec4(floor(scene.rgb * levels + 0.5) / levels, scene.a);
}
