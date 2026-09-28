#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

// core/area_block, with every block moved by its own transform: BlastBlockOf maps a vertex (gl_VertexIndex: this is
// compiled to SPIR-V) to its block, and
// BlastTransforms holds three texels per block (rotation; position and scale; clump center and alpha).

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif
uniform isamplerBuffer BlastBlockOf;
uniform samplerBuffer BlastTransforms;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

void main() {
    int block = texelFetch(BlastBlockOf, gl_VertexIndex).r;
    vec4 rotation = texelFetch(BlastTransforms, block * 3);
    vec4 positionScale = texelFetch(BlastTransforms, block * 3 + 1);
    vec4 centerAlpha = texelFetch(BlastTransforms, block * 3 + 2);

    vec3 local = (Position + ModelOffset - centerAlpha.xyz) * positionScale.w;
    vec3 turned = local + 2.0 * cross(rotation.xyz, cross(rotation.xyz, local) + rotation.w * local);
    vec3 pos = turned + positionScale.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vec3 relative = (TextureMat * vec4(pos, 1.0)).xyz;
    sphericalVertexDistance = fog_spherical_distance(relative);
    cylindricalVertexDistance = fog_cylindrical_distance(relative);
    #ifndef OIT_ALPHA_ONLY
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    #else
    vertexColor = Color;
    #endif
    vertexColor.a *= centerAlpha.w;
    texCoord0 = UV0;
}
