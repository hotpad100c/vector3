#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D DepthSampler;
layout(std140) uniform DOFProjection {
    vec4 Projection; // m22, m32, m23, m33
    vec4 Convention; // x: depth is already NDC z, y: depth of the far plane, where the sky is
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (abs(depth - Convention.y) < 1e-7) {
        fragColor = vec4(60000.0, 0.0, 0.0, 1.0);
        return;
    }
    float ndc = Convention.x > 0.5 ? depth : depth * 2.0 - 1.0;
    float denominator = ndc * Projection.z - Projection.x;
    float dist = abs(denominator) < 1e-7 ? 60000.0 : (ndc * Projection.w - Projection.y) / denominator;
    fragColor = vec4(clamp(dist, 0.0, 60000.0), 0.0, 0.0, 1.0);
}
