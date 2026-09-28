#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
layout(std140) uniform DOFSettings {
    vec4 Focus;   // focus distance, half sharp range, mode, max radius in full-res pixels
    vec4 Texel;   // full-res texel, half-res texel
    vec4 Flags;   // overlay, blur
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float coc(float d) {
    float delta = d - Focus.x;
    float c = clamp(sign(delta) * max(abs(delta) - Focus.y, 0.0) / max(d, 0.05), -1.0, 1.0);
    if (Focus.z > 0.5 && Focus.z < 1.5) c = min(c, 0.0);
    if (Focus.z > 1.5) c = max(c, 0.0);
    return c;
}

void main() {
    vec2 o = Texel.xy * 0.5;
    float d = min(min(texture(DistanceSampler, texCoord + vec2(-o.x, -o.y)).r,
                      texture(DistanceSampler, texCoord + vec2(o.x, -o.y)).r),
                  min(texture(DistanceSampler, texCoord + vec2(-o.x, o.y)).r,
                      texture(DistanceSampler, texCoord + vec2(o.x, o.y)).r));
    fragColor = vec4(texture(InSampler, texCoord).rgb, coc(d));
}
