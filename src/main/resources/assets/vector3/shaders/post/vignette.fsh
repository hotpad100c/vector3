#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform VignetteSettings { vec4 Settings; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 scene = texture(InSampler, texCoord);
    float radius = length((texCoord - 0.5) * vec2(Settings.z, 1.0) * 2.0) / max(Settings.z, 1.0);
    scene.rgb *= 1.0 - Settings.x * smoothstep(Settings.y, Settings.y + 0.5, radius);
    fragColor = scene;
}
