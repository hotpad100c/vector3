#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D LutSampler;
layout(std140) uniform LutSettings { vec4 Settings; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
vec3 lookup(vec3 colour) {
    vec3 scaled = clamp(colour, 0.0, 1.0) * 15.0;
    float low = floor(scaled.b), high = min(low + 1.0, 15.0);
    vec2 uv = (scaled.rg + 0.5) / vec2(256.0, 16.0);
    vec3 a = texture(LutSampler, vec2(uv.x + low / 16.0, uv.y)).rgb;
    vec3 b = texture(LutSampler, vec2(uv.x + high / 16.0, uv.y)).rgb;
    return mix(a, b, fract(scaled.b));
}
void main() {
    vec4 scene = texture(InSampler, texCoord);
    fragColor = vec4(mix(scene.rgb, lookup(scene.rgb), clamp(Settings.x, 0.0, 1.0)), scene.a);
}
