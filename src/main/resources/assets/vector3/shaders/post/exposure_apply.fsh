#version 330
uniform sampler2D InSampler;
uniform sampler2D ExposureSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 scene = texture(InSampler, texCoord);
    float exposure = texture(ExposureSampler, vec2(0.5)).r;
    if (isnan(exposure) || isinf(exposure) || exposure <= 0.0) exposure = 1.0;
    fragColor = vec4(scene.rgb * exposure, scene.a);
}
