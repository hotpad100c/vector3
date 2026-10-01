#version 330
uniform sampler2D InSampler;
uniform sampler2D DirtSampler;
layout(std140) uniform BloomComposite { vec4 Settings; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 bloom = texture(InSampler, texCoord).rgb * Settings.rgb;
    vec3 dirt = texture(DirtSampler, texCoord).rgb;
    fragColor = vec4(bloom * (1.0 + dirt * Settings.a), 1.0);
}
