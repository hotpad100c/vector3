#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    float sum = 0.0;
    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
        vec2 uv = (vec2(x, y) + 0.5) / 8.0;
        vec3 c = texture(InSampler, uv).rgb;
        sum += log(max(dot(c, vec3(0.2126, 0.7152, 0.0722)), 0.001));
    }
    fragColor = vec4(exp(sum / 64.0), 0.0, 0.0, 1.0);
}
