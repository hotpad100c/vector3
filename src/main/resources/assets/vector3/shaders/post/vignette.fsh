#version 330
uniform sampler2D InSampler;
layout(std140) uniform VignetteSettings { vec4 Settings; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 scene = texture(InSampler, texCoord);
    float radius = length((texCoord - 0.5) * vec2(Settings.z, 1.0) * 2.0) / max(Settings.z, 1.0);
    scene.rgb *= 1.0 - Settings.x * smoothstep(Settings.y, Settings.y + 0.5, radius);
    fragColor = scene;
}
