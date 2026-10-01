#version 330
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    float sum = 0.0;
    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
        vec2 uv = (vec2(x, y) + 0.5) / 8.0;
        vec3 c = texture(InSampler, uv).rgb;
        if (any(isnan(c)) || any(isinf(c))) c = vec3(0.5);
        sum += log(max(dot(c, vec3(0.2126, 0.7152, 0.0722)), 0.001));
    }
    fragColor = vec4(exp(sum / 64.0), 0.0, 0.0, 1.0);
}
