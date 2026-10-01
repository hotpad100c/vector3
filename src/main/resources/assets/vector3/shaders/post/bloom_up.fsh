#version 330
uniform sampler2D PrevSampler;
uniform sampler2D CurrentSampler;
layout(std140) uniform BloomStep { vec4 Step; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 d = Step.xy;
    vec3 wide = texture(PrevSampler, texCoord).rgb * 4.0;
    wide += (texture(PrevSampler, texCoord + vec2(d.x, 0.0)).rgb
           + texture(PrevSampler, texCoord - vec2(d.x, 0.0)).rgb
           + texture(PrevSampler, texCoord + vec2(0.0, d.y)).rgb
           + texture(PrevSampler, texCoord - vec2(0.0, d.y)).rgb) * 2.0;
    wide += texture(PrevSampler, texCoord + d).rgb + texture(PrevSampler, texCoord - d).rgb;
    wide += texture(PrevSampler, texCoord + vec2(d.x, -d.y)).rgb;
    wide += texture(PrevSampler, texCoord + vec2(-d.x, d.y)).rgb;
    fragColor = vec4(mix(texture(CurrentSampler, texCoord).rgb, wide / 16.0, Step.z), 1.0);
}
