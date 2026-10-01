#version 330

uniform sampler2D InSampler;
layout(std140) uniform ColorGradingSettings {
    vec4 Basic0; // exposure, temperature, tint, hue
    vec4 Basic1; // saturation, contrast, tonemap
    vec4 MixerRed;
    vec4 MixerGreen;
    vec4 MixerBlue;
    vec4 Lift;
    vec4 Gamma;
    vec4 Gain;
};
in vec2 texCoord;
out vec4 fragColor;

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec4 source = texture(InSampler, texCoord);
    vec3 color = source.rgb * exp2(Basic0.x);
    float temperature = Basic0.y * 0.0015;
    float tint = Basic0.z * 0.001;
    color *= vec3(1.0 + temperature + tint * 0.5, 1.0 - tint, 1.0 - temperature + tint * 0.5);

    float yi = dot(color, vec3(0.299, 0.587, 0.114));
    float ii = dot(color, vec3(0.596, -0.274, -0.322));
    float qi = dot(color, vec3(0.211, -0.523, 0.312));
    float angle = radians(Basic0.w);
    float i = ii * cos(angle) - qi * sin(angle);
    float q = ii * sin(angle) + qi * cos(angle);
    color = vec3(yi + 0.956 * i + 0.621 * q,
                 yi - 0.272 * i - 0.647 * q,
                 yi - 1.106 * i + 1.703 * q);

    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luminance), color, 1.0 + Basic1.x / 100.0);
    color = (color - 0.5) * (1.0 + Basic1.y / 100.0) + 0.5;
    color = vec3(dot(color, MixerRed.rgb), dot(color, MixerGreen.rgb), dot(color, MixerBlue.rgb));
    color = pow(max((color + Lift.rgb) * Gain.rgb, vec3(0.0)),
                vec3(1.0) / max(Gamma.rgb, vec3(0.01)));
    if (Basic1.z > 1.5) color = aces(color);
    else if (Basic1.z > 0.5) color = color / (1.0 + max(color - 0.5, 0.0));
    fragColor = vec4(max(color, 0.0), source.a);
}
