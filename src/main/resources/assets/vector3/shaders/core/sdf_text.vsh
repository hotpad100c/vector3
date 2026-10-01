#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// PARTICLE vertex format: Position, UV0, Color, UV2. UV2 is repurposed to carry per-glyph style:
// x = outline colour as RGB565, y = outline width in 1/32 | flags << 8 (bit 0 bold, bit 1 outline).
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

out vec2 texCoord0;
out vec4 vertexColor;
flat out vec3 outlineColor;
flat out int styleFlags;
flat out float outlineWidth;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    outlineColor = vec3(float((UV2.x >> 11) & 0x1F) / 31.0, float((UV2.x >> 5) & 0x3F) / 63.0, float(UV2.x & 0x1F) / 31.0);
    outlineWidth = float(UV2.y & 0xFF) / 32.0;
    styleFlags = (UV2.y >> 8) & 0xFF;
}
