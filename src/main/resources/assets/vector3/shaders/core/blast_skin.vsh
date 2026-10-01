#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

// core/area_block, with every block moved by its own transform: BlastBlockOf maps a vertex (gl_VertexID: this is
// compiled to SPIR-V) to its block, and
// BlastTransforms holds three texels per block (rotation; position and scale; clump center and alpha).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif
uniform isamplerBuffer BlastBlockOf;
uniform samplerBuffer BlastTransforms;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    int block = texelFetch(BlastBlockOf, gl_VertexID).r;
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
