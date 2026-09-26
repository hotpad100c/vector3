#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D PrevSampler;
uniform sampler2D CurrentSampler;

layout(std140) uniform GlowStep {
    vec4 Step;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 t = Step.xy;
    vec4 sum = texture(PrevSampler, texCoord) * 4.0;
    sum += (texture(PrevSampler, texCoord + vec2(t.x, 0.0)) + texture(PrevSampler, texCoord - vec2(t.x, 0.0))
          + texture(PrevSampler, texCoord + vec2(0.0, t.y)) + texture(PrevSampler, texCoord - vec2(0.0, t.y))) * 2.0;
    sum += texture(PrevSampler, texCoord + t) + texture(PrevSampler, texCoord - t)
         + texture(PrevSampler, texCoord + vec2(t.x, -t.y)) + texture(PrevSampler, texCoord + vec2(-t.x, t.y));
    fragColor = mix(texture(CurrentSampler, texCoord), sum / 16.0, Step.z);
}
