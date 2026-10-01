#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vanilla glyph vertices; the packed light is repurposed: UV2.x = glow strength in eighths.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out vec2 texCoord0;
out vec4 vertexColor;
flat out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    strength = float(UV2.x & 63) / 8.0;
}
