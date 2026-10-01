#version 330
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
layout(std140) uniform MotionSettings {
    vec4 CurrentRight;
    vec4 CurrentUp;
    vec4 CurrentForward;
    vec4 PreviousRight;
    vec4 PreviousUp;
    vec4 PreviousForward;
    vec4 PositionDelta;
    vec4 Screen;
};
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 center = texture(InSampler, texCoord);
    float depth = texture(DistanceSampler, texCoord).r;
    if (depth >= 50000.0 || depth < 0.01) { fragColor = center; return; }
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 worldFromCurrent = depth * (CurrentForward.xyz
            + CurrentRight.xyz * ndc.x / CurrentRight.w
            + CurrentUp.xyz * ndc.y / CurrentUp.w);
    vec3 fromPrevious = PositionDelta.xyz + worldFromCurrent;
    float z = dot(fromPrevious, PreviousForward.xyz);
    if (z <= 0.01) { fragColor = center; return; }
    vec2 previousUv = vec2(dot(fromPrevious, PreviousRight.xyz) * CurrentRight.w,
                           dot(fromPrevious, PreviousUp.xyz) * CurrentUp.w) / z * 0.5 + 0.5;
    vec2 velocity = (texCoord - previousUv) * CurrentForward.w;
    float pixels = length(velocity * Screen.xy);
    velocity *= min(1.0, Screen.z / max(pixels, 0.001));
    vec3 sum = vec3(0.0);
    int count = int(PreviousRight.w + 0.5);
    for (int i = 0; i < 32; i++) {
        if (i >= count) break;
        float t = (float(i) / max(float(count - 1), 1.0)) - 0.5;
        sum += texture(InSampler, clamp(texCoord + velocity * t, vec2(0.0), vec2(1.0))).rgb;
    }
    fragColor = vec4(sum / max(float(count), 1.0), center.a);
}
