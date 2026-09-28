#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler; // rgb colour, a signed CoC (negative is in front of the focus plane)
layout(std140) uniform DOFSettings {
    vec4 Focus;
    vec4 Texel;
    vec4 Flags;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float GOLDEN_ANGLE = 2.39996323;

void main() {
    float maxRadius = Focus.w * 0.5;
    vec4 center = texture(InSampler, texCoord);
    float centerSize = abs(center.a) * maxRadius;
    vec3 colour = center.rgb;
    float total = 1.0, near = 0.0;
    // Spiral gather; the step keeps the sample count near 120 however large the radius gets.
    float spacing = max(0.5, maxRadius * maxRadius / 240.0);
    float radius = spacing;
    for (int i = 0; i < 256 && radius < maxRadius; i++) {
        float angle = float(i) * GOLDEN_ANGLE;
        vec4 s = texture(InSampler, texCoord + vec2(cos(angle), sin(angle)) * Texel.zw * radius);
        float size = abs(s.a) * maxRadius;
        // Sharp or less blurred background must not bleed onto what is in front of it.
        if (s.a > center.a) size = min(size, centerSize * 2.0);
        float m = smoothstep(radius - 0.5, radius + 0.5, size);
        colour += mix(colour / total, s.rgb, m);
        total += 1.0;
        if (s.a < 0.0 && s.a < center.a - 0.02) near += m;
        radius += spacing / radius;
    }
    fragColor = vec4(colour / total, total > 1.0 ? near / (total - 1.0) : 0.0);
}
