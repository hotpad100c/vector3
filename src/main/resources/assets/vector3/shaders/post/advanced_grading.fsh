#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform AdvancedGradingSettings {
    vec4 SplitShadows;
    vec4 SplitHighlights;
    vec4 Shadows;
    vec4 Midtones;
    vec4 Highlights;
    vec4 Controls;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 source = texture(InSampler, texCoord);
    vec3 c = source.rgb;
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float shadow = 1.0 - smoothstep(0.0, Controls.y, lum);
    float highlight = smoothstep(Controls.z, 1.0, lum);
    float mid = max(0.0, 1.0 - shadow - highlight);
    c *= Shadows.rgb * shadow + Midtones.rgb * mid + Highlights.rgb * highlight;
    float split = clamp(lum + Controls.x * 0.5, 0.0, 1.0);
    c += SplitShadows.rgb * (1.0 - split) * 0.25 + SplitHighlights.rgb * split * 0.25;
    fragColor = vec4(max(c, 0.0), source.a);
}
