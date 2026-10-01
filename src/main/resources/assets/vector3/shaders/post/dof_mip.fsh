#version 330
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 t = 1.0 / vec2(textureSize(InSampler, 0));
    fragColor = 0.25 * (texture(InSampler, texCoord + vec2(-t.x, -t.y)) + texture(InSampler, texCoord + vec2(t.x, -t.y))
            + texture(InSampler, texCoord + vec2(-t.x, t.y)) + texture(InSampler, texCoord + vec2(t.x, t.y)));
}
