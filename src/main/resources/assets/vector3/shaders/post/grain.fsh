#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform GrainSettings { vec4 Grain; vec4 Screen; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

void main() {
    vec4 source = texture(InSampler, texCoord);
    vec2 cell = floor(texCoord * Screen.xy / max(Grain.y, 1.0));
    float frame = floor(Grain.z);
    vec3 noise = vec3(hash(vec3(cell, frame)), hash(vec3(cell + 17.0, frame)),
                      hash(vec3(cell + 41.0, frame))) * 2.0 - 1.0;
    if (Grain.w < 0.5) noise = vec3(noise.r);
    float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    float midtone = 1.0 - abs(luminance * 2.0 - 1.0) * 0.5;
    fragColor = vec4(clamp(source.rgb + noise * Grain.x * midtone, 0.0, 1.0), source.a);
}
