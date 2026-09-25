#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform GlowBlur {
    vec4 Step;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// 9-tap Gaussian folded into 5 linearly filtered fetches.
void main() {
    vec2 step = Step.xy;
    vec4 sum = texture(InSampler, texCoord) * 0.2270270270;
    sum += texture(InSampler, texCoord + step * 1.3846153846) * 0.3162162162;
    sum += texture(InSampler, texCoord - step * 1.3846153846) * 0.3162162162;
    sum += texture(InSampler, texCoord + step * 3.2307692308) * 0.0702702703;
    sum += texture(InSampler, texCoord - step * 3.2307692308) * 0.0702702703;
    fragColor = sum;
}
