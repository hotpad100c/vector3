#version 330
uniform sampler2D InSampler;
layout(std140) uniform GradingCurves {
    vec4 Primary[64];
    vec4 Versus[64];
};
in vec2 texCoord;
out vec4 fragColor;

vec4 samplePrimary(float x) {
    float position = clamp(x, 0.0, 1.0) * 63.0;
    int low = int(floor(position));
    return mix(Primary[low], Primary[min(low + 1, 63)], fract(position));
}

vec4 sampleVersus(float x) {
    float position = clamp(x, 0.0, 1.0) * 63.0;
    int low = int(floor(position));
    return mix(Versus[low], Versus[min(low + 1, 63)], fract(position));
}

vec3 rgb2hsv(vec3 c) {
    vec4 k = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, k.wz), vec4(c.gb, k.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1e-6)), d / (q.x + 1e-6), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

void main() {
    vec4 source = texture(InSampler, texCoord);
    vec3 c = clamp(source.rgb, 0.0, 1.0);
    c = vec3(samplePrimary(c.r).y, samplePrimary(c.g).z,
             samplePrimary(c.b).w);
    c = vec3(samplePrimary(c.r).x, samplePrimary(c.g).x,
             samplePrimary(c.b).x);
    vec3 hsv = rgb2hsv(c);
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec4 hueCurves = sampleVersus(hsv.x);
    vec4 satCurves = sampleVersus(hsv.y);
    vec4 lumCurves = sampleVersus(lum);
    hsv.x = fract(hsv.x + hueCurves.x - 0.5);
    hsv.y = clamp(hsv.y * (hueCurves.y * 2.0) * (satCurves.z * 2.0)
                  * (lumCurves.w * 2.0), 0.0, 1.0);
    fragColor = vec4(hsv2rgb(hsv), source.a);
}
