#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
uniform sampler2D FocusSampler;    // smoothed autofocus distance, 1 x 1
layout(std140) uniform DOFSettings {
    vec4 Focus; // focus distance, half sharp range, mode, max radius in full-res pixels
    vec4 Lens;  // aperture, focal scale, tilt x, tilt y
    vec4 Shape; // sides (0 is a circle), samples, rings, rotation in radians
    vec4 Flags; // overlay, chromatic, anamorphic, autofocus
    vec4 Texel; // full-res texel size, height / width, chromatic strength
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float focusAt(vec2 uv) {
    float focus = Flags.w > 0.5 ? texture(FocusSampler, vec2(0.5)).r : Focus.x;
    return focus * exp2((Lens.z * (uv.x * 2.0 - 1.0) + Lens.w * (uv.y * 2.0 - 1.0)) * 0.1);
}

// Signed circle of confusion in [-1, 1], negative in front of the focus plane.
float coc(float d, vec2 uv) {
    float focus = focusAt(uv);
    float x;
    if (Focus.z > 2.5) {
        x = d / max(focus, 0.01) * 0.3;
    } else {
        float delta = d - focus;
        x = sign(delta) * max(abs(delta) - Focus.y, 0.0) / max(d, 0.05);
        if (Focus.z > 0.5 && Focus.z < 1.5) x = min(x, 0.0);
        if (Focus.z > 1.5) x = max(x, 0.0);
    }
    x *= Lens.x * Lens.y;
    return x / sqrt(x * x + 0.1);
}

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, coc(texture(DistanceSampler, texCoord).r, texCoord));
}
