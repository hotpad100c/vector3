#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

// vanilla core/block, except that the area's mesh is in its source region's world coordinates, so the fog
// distance comes from TextureMat (source to camera-relative destination) instead of the raw position.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vec3 relative = (TextureMat * vec4(pos, 1.0)).xyz;
    sphericalVertexDistance = fog_spherical_distance(relative);
    cylindricalVertexDistance = fog_cylindrical_distance(relative);
    #ifndef OIT_ALPHA_ONLY
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    #else
    vertexColor = Color;
    #endif
    texCoord0 = UV0;
}
