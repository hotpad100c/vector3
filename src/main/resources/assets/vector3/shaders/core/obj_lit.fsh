#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec3 worldPos;
layout(location = 3) in vec3 viewPos;

layout(location = 0) out vec4 fragColor;

const vec3 LIGHT0 = vec3(0.16169, 0.80845, -0.56592);
const vec3 LIGHT1 = vec3(-0.16169, 0.80845, 0.56592);

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    vec3 normal = cross(dFdx(worldPos), dFdy(worldPos));
    if (cross(dFdx(viewPos), dFdy(viewPos)).z < 0.0) normal = -normal;
    normal = dot(normal, normal) > 0.0 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    float light = min(1.0, (max(dot(LIGHT0, normal), 0.0) + max(dot(LIGHT1, normal), 0.0)) * 0.6 + 0.4);
    fragColor = vec4(color.rgb * light, color.a) * ColorModulator;
}
