#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;       // the frame, for its alpha
uniform sampler2D MipSampler;      // rgb frame, a signed CoC, full mip chain
uniform sampler2D NearSampler;     // dilated foreground blur in half-res pixels
uniform sampler2D DistanceSampler; // view distance in blocks
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

const float PI = 3.14159265;

float focusAt(vec2 uv) {
    float focus = Flags.w > 0.5 ? texture(FocusSampler, vec2(0.5)).r : Focus.x;
    return focus * exp2((Lens.z * (uv.x * 2.0 - 1.0) + Lens.w * (uv.y * 2.0 - 1.0)) * 0.1);
}

// Point on the bokeh outline; polygons follow halcy's hexablur (shadertoy 4tK3WK), as Complementary does.
vec2 shapeOffset(float angle) {
    float r = 1.0;
    if (Shape.x > 2.5) {
        float segment = 2.0 * PI / Shape.x;
        r = cos(PI / Shape.x) / cos(mod(angle, segment) - PI / Shape.x);
    }
    float a = angle + Shape.w;
    return vec2(sin(a), cos(a)) * r;
}

void main() {
    vec4 center = textureLod(MipSampler, texCoord, 0.0);
    vec3 colour = center.rgb;
    float radius = abs(center.a) * Focus.w;
    float kernel = radius;
    if (Focus.w > 0.5) kernel = max(radius, texture(NearSampler, texCoord).r * 2.0);
    if (kernel >= 0.5) {
        int rings = max(1, int(Shape.z + 0.5));
        // Each tap reads a mip as wide as the gap between rings, so few taps still cover the whole disc.
        float lod = log2(max(kernel / float(rings), 1.0));
        vec2 scale = Texel.xy * (Flags.z > 0.5 ? vec2(0.5, 1.5) : vec2(1.0));
        vec2 fromCenter = texCoord - 0.5;
        vec2 aberration = sign(fromCenter) * sqrt(abs(fromCenter)) * vec2(1.0, Texel.z) * kernel * 0.35 * Texel.w * Texel.xy;
        vec3 sum = colour;
        float total = 1.0;
        for (int ring = 1; ring <= rings; ring++) {
            float rf = float(ring) / float(rings);
            int count = max(6, int(Shape.y * 10.0 * rf + 0.5));
            float stagger = (ring % 2) == 0 ? 0.5 : 0.0;
            for (int i = 0; i < count; i++) {
                vec2 offset = shapeOffset((float(i) + stagger) * 2.0 * PI / float(count)) * rf * kernel;
                vec2 uv = texCoord + offset * scale;
                vec4 s = textureLod(MipSampler, uv, lod);
                if (Flags.y > 0.5) {
                    s.r = textureLod(MipSampler, uv + aberration, lod).r;
                    s.b = textureLod(MipSampler, uv - aberration, lod).b;
                }
                float r = length(offset);
                float size = abs(s.a) * Focus.w;
                // Sharper background must not bleed onto what is in front of it.
                if (s.a > center.a) size = min(size, radius * 2.0);
                float m = smoothstep(r - 1.0, r + 1.0, size);
                sum += mix(sum / total, s.rgb, m);
                total += 1.0;
            }
        }
        colour = sum / total;
    }
    if (Flags.x > 0.5) {
        float d = texture(DistanceSampler, texCoord).r;
        float delta = d - focusAt(texCoord);
        if (abs(delta) <= Focus.y) colour = mix(colour, vec3(0.25, 0.85, 1.0), 0.3);
        float width = clamp(fwidth(delta), 0.002, max(abs(focusAt(texCoord)) * 0.05, 0.01)) * 1.5;
        colour = mix(colour, vec3(1.0, 0.82, 0.2), (1.0 - smoothstep(0.0, width, abs(delta))) * 0.9);
    }
    fragColor = vec4(colour, texture(InSampler, texCoord).a);
}
