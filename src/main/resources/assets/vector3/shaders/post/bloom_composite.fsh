#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DirtSampler;
layout(std140) uniform BloomComposite { vec4 Settings; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec3 bloom = texture(InSampler, texCoord).rgb * Settings.rgb;
    vec3 dirt = texture(DirtSampler, texCoord).rgb;
    fragColor = vec4(bloom * (1.0 + dirt * Settings.a), 1.0);
}
