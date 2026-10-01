#version 330
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;

// Halves the frame: four bilinear taps cover the 4 x 4 source texels around each output pixel.
void main() {
    vec2 t = 1.0 / vec2(textureSize(InSampler, 0));
    fragColor = 0.25 * (texture(InSampler, texCoord + vec2(-t.x, -t.y)) + texture(InSampler, texCoord + vec2(t.x, -t.y))
            + texture(InSampler, texCoord + vec2(-t.x, t.y)) + texture(InSampler, texCoord + vec2(t.x, t.y)));
}
