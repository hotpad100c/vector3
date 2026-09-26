#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform GlowStep {
    vec4 Step;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 t = Step.xy;
    vec4 a = texture(InSampler, texCoord + t * vec2(-2.0, 2.0));
    vec4 b = texture(InSampler, texCoord + t * vec2(0.0, 2.0));
    vec4 c = texture(InSampler, texCoord + t * vec2(2.0, 2.0));
    vec4 d = texture(InSampler, texCoord + t * vec2(-2.0, 0.0));
    vec4 e = texture(InSampler, texCoord);
    vec4 f = texture(InSampler, texCoord + t * vec2(2.0, 0.0));
    vec4 g = texture(InSampler, texCoord + t * vec2(-2.0, -2.0));
    vec4 h = texture(InSampler, texCoord + t * vec2(0.0, -2.0));
    vec4 i = texture(InSampler, texCoord + t * vec2(2.0, -2.0));
    vec4 j = texture(InSampler, texCoord + t * vec2(-1.0, 1.0));
    vec4 k = texture(InSampler, texCoord + t * vec2(1.0, 1.0));
    vec4 l = texture(InSampler, texCoord + t * vec2(-1.0, -1.0));
    vec4 m = texture(InSampler, texCoord + t * vec2(1.0, -1.0));
    fragColor = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
}
