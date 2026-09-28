#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform DOFSettings {
    vec4 Focus; // focus distance, half sharp range, mode, max radius in full-res pixels
    vec4 Lens;  // aperture, focal scale, tilt x, tilt y
    vec4 Shape; // sides (0 is a circle), samples, rings, rotation in radians
    vec4 Flags; // overlay, chromatic, anamorphic, autofocus
    vec4 Texel; // full-res texel size, height / width, chromatic strength
};
layout(std140) uniform DOFDirection {
    vec4 Direction; // xy: half-res texel step, z: source holds the signed CoC in alpha
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Largest foreground blur, in half-res pixels, that reaches this pixel along one axis.
void main() {
    float maxRadius = Focus.w * 0.5;
    float stepSize = max(1.0, maxRadius / 32.0);
    float result = 0.0;
    for (float x = -maxRadius; x <= maxRadius + 0.001; x += stepSize) {
        vec4 s = texture(InSampler, texCoord + Direction.xy * x);
        float size = Direction.z > 0.5 ? max(-s.a, 0.0) * maxRadius : s.r;
        if (size >= abs(x)) result = max(result, size);
    }
    fragColor = vec4(result, 0.0, 0.0, 1.0);
}
