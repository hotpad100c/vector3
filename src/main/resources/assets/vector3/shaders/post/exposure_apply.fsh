#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D ExposureSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 scene = texture(InSampler, texCoord);
    fragColor = vec4(scene.rgb * texture(ExposureSampler, vec2(0.5)).r, scene.a);
}
