#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

void main() {
    gl_FragDepth = texture(InSampler, texCoord).r;
}
