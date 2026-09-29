#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform GodRaysBlur {
    vec4 Ray; // length (share of the way to the sun), decay, samples, falloff
    vec4 Sun; // sun screen position, aspect ratio
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float noise(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

// Radial blur towards the sun. Taps outside the screen see no light, so a sun beyond the edge still works.
void main() {
    int count = int(Ray.z + 0.5);
    vec2 delta = (Sun.xy - texCoord) * Ray.x / float(count);
    vec2 uv = texCoord + delta * noise(gl_FragCoord.xy);
    vec3 sum = vec3(0.0);
    float total = 0.0;
    float weight = 1.0;
    for (int i = 0; i < 128; i++) {
        if (i >= count) break;
        if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
            vec2 d = (uv - Sun.xy) * vec2(Sun.z, 1.0);
            float glow = 1.0 / (1.0 + Ray.w * dot(d, d) * 16.0);
            sum += texture(InSampler, uv).rgb * (glow * weight);
        }
        total += weight;
        uv += delta;
        weight *= Ray.y;
    }
    fragColor = vec4(sum / max(total, 1e-4), 1.0);
}
