#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
flat in vec3 outlineColor;
flat in int styleFlags;
flat in float outlineWidth;

out vec4 fragColor;

// Distance field: 0.5 is the glyph edge, one pixel of the SDF bitmap is ~0.084 (see SdfFont).
const float EDGE = 0.5;
const float BOLD = 0.07;
const float OUTLINE = 0.22;

void main() {
    float dist = texture(Sampler0, texCoord0).a;
    // Screen-space derivative keeps edges ~1px wide at any size, which is what keeps SDF text sharp.
    float smoothing = max(fwidth(dist) * 0.7, 1.0 / 255.0);
    float fillEdge = (styleFlags & 1) != 0 ? EDGE - BOLD : EDGE;
    float fill = smoothstep(fillEdge - smoothing, fillEdge + smoothing, dist);

    vec4 color;
    if ((styleFlags & 2) != 0) {
        // The field runs out a little before 0, which caps how wide an outline can get.
        float outlineEdge = fillEdge - min(OUTLINE * outlineWidth, fillEdge - 0.03);
        float outer = smoothstep(outlineEdge - smoothing, outlineEdge + smoothing, dist);
        color = vec4(mix(outlineColor, vertexColor.rgb, fill), vertexColor.a * outer);
    } else {
        color = vec4(vertexColor.rgb, vertexColor.a * fill);
    }
    color *= ColorModulator;
    if (color.a < 0.01) {
        discard;
    }
    fragColor = color;
}
