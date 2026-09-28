#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D BlurSampler;
uniform sampler2D DistanceSampler;
layout(std140) uniform DOFSettings {
    vec4 Focus;   // focus distance, half sharp range, mode, max radius in full-res pixels
    vec4 Texel;
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
    vec4 scene = texture(InSampler, texCoord);
    float d = texture(DistanceSampler, texCoord).r;
    vec3 colour = scene.rgb;
    if (Flags.y > 0.5) {
        vec4 blurred = texture(BlurSampler, texCoord);
        float t = max(smoothstep(0.5, 2.0, abs(coc(d)) * Focus.w), clamp(blurred.a * 2.0, 0.0, 1.0));
        colour = mix(colour, blurred.rgb, t);
    }
    if (Flags.x > 0.5) {
        float delta = d - Focus.x;
        if (abs(delta) <= Focus.y) colour = mix(colour, vec3(0.25, 0.85, 1.0), 0.3);
        float width = clamp(fwidth(d), 0.002, Focus.x * 0.05) * 1.5;
        colour = mix(colour, vec3(1.0, 0.82, 0.2), (1.0 - smoothstep(0.0, width, abs(delta))) * 0.9);
    }
    fragColor = vec4(colour, scene.a);
}
