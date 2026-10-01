#version 330
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
layout(std140) uniform GodRaysMask {
    vec4 Mask; // sky as a light source, bright pixels as light sources, their threshold, brightness floor of the sky
};
in vec2 texCoord;
out vec4 fragColor;

// Keeps what can emit light and blacks out the rest, so everything else works as an occluder.
void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    bool sky = texture(DistanceSampler, texCoord).r > 50000.0;
    vec3 source = vec3(0.0);
    if (Mask.y > 0.5) source = color * max(luma - Mask.z, 0.0) / max(1.0 - Mask.z, 0.001);
    if (sky && Mask.x > 0.5) source = max(source, vec3(max(luma, Mask.w)));
    fragColor = vec4(source, 1.0);
}
