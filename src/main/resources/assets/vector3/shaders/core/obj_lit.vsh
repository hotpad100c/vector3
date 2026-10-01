#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 worldPos;
out vec3 viewPos;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    texCoord0 = UV0;
    vertexColor = Color;
    worldPos = (TextureMat * vec4(Position, 1.0)).xyz;
    viewPos = view.xyz;
}
