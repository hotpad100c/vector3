#version 330
uniform sampler2D InSampler;
uniform sampler2D LightSampler;
in vec2 texCoord;
out vec4 fragColor;

float dither(vec2 pixel) {
    return fract(sin(dot(pixel, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec3 light = max(texture(LightSampler, texCoord).rgb, vec3(0.0));
    vec3 color = scene.rgb + (1.0 - scene.rgb) * (1.0 - exp(-light));
    if (max(max(light.r, light.g), light.b) > 0.001)
        color += dither(gl_FragCoord.xy) / 255.0;
    fragColor = vec4(clamp(color, 0.0, 1.0), scene.a);
}
