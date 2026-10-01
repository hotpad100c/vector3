#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
flat in float strength;

out vec4 fragColor;

void main() {
#ifdef GRAYSCALE
    vec4 color = texture(Sampler0, texCoord0).rrrr * vertexColor * ColorModulator;
#else
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
#endif
    if (color.a < 0.1) {
        discard;
    }
    color.rgb *= strength;
    fragColor = color;
}
