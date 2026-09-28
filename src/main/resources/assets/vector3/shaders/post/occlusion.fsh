#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
layout(std140) uniform OcclusionSettings { vec4 Occlusion; vec4 Screen; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 scene = texture(InSampler, texCoord);
    float depth = texture(DistanceSampler, texCoord).r;
    if (depth >= 50000.0 || depth <= 0.01) { fragColor = scene; return; }
    float angle = fract(sin(dot(floor(texCoord / Screen.xy), vec2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
    float pixelRadius = clamp(Occlusion.y * abs(Screen.z) / max(depth, 0.1) / Screen.y, 1.0, 48.0);
    float hidden = 0.0;
    int count = int(Occlusion.w + 0.5);
    for (int i = 0; i < 32; i++) {
        if (i >= count) break;
        float theta = angle + float(i) * 2.39996323;
        float r = sqrt((float(i) + 0.5) / float(count));
        vec2 offset = vec2(cos(theta), sin(theta)) * pixelRadius * r * Screen.xy;
        float sampleDepth = texture(DistanceSampler, clamp(texCoord + offset, vec2(0.0), vec2(1.0))).r;
        float difference = depth - sampleDepth - Occlusion.z;
        hidden += smoothstep(0.0, Occlusion.y, difference) * (1.0 - r * 0.4);
    }
    float occlusion = hidden / float(count);
    fragColor = vec4(scene.rgb * (1.0 - clamp(occlusion * Occlusion.x, 0.0, 0.9)), scene.a);
}
