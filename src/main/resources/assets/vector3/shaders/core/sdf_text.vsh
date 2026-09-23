#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// PARTICLE vertex format: Position, UV0, Color, UV2. UV2 is repurposed to carry per-glyph style:
// x = outline R | outline G << 8, y = outline B | flags << 8 (bit 0 bold, bit 1 outline).
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV2;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out vec3 outlineColor;
layout(location = 3) flat out int styleFlags;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    outlineColor = vec3(float(UV2.x & 0xFF), float((UV2.x >> 8) & 0xFF), float(UV2.y & 0xFF)) / 255.0;
    styleFlags = (UV2.y >> 8) & 0xFF;
}
