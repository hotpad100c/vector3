#version 330
uniform sampler2D InSampler;
layout(std140) uniform BloomStep { vec4 Step; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 d = Step.xy;
    vec3 c = texture(InSampler, texCoord).rgb * 4.0;
    c += texture(InSampler, texCoord + d).rgb;
    c += texture(InSampler, texCoord - d).rgb;
    c += texture(InSampler, texCoord + vec2(d.x, -d.y)).rgb;
    c += texture(InSampler, texCoord + vec2(-d.x, d.y)).rgb;
    if (Step.z > 0.5) {
        c += texture(InSampler, texCoord + vec2(d.x, 0.0)).rgb;
        c += texture(InSampler, texCoord - vec2(d.x, 0.0)).rgb;
        c += texture(InSampler, texCoord + vec2(0.0, d.y)).rgb;
        c += texture(InSampler, texCoord - vec2(0.0, d.y)).rgb;
        c /= 12.0;
    } else c /= 8.0;
    fragColor = vec4(c, 1.0);
}
