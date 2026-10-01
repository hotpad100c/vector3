#version 330
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
layout(std140) uniform FlareSettings { vec4 Flare; vec4 Detail; vec4 Sun; };
in vec2 texCoord;
out vec4 fragColor;

float visibleSun() {
    float visible = 0.0;
    for (int i = 0; i < 9; i++) {
        float angle = float(i) * 6.2831853 / 8.0;
        vec2 offset = i == 0 ? vec2(0.0) : vec2(cos(angle), sin(angle))
                * 0.012 / vec2(Detail.w, 1.0);
        visible += texture(DistanceSampler, Sun.xy + offset).r > 50000.0 ? (i == 0 ? 2.0 : 1.0) : 0.0;
    }
    return visible / 10.0;
}

float disc(vec2 uv, vec2 center, float radius) {
    float distance = length((uv - center) * vec2(Detail.w, 1.0));
    return 1.0 - smoothstep(radius * 0.35, radius, distance);
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    float visibility = visibleSun() * Sun.z;
    // The moon's disc isn't bright enough to gate on, so it flares in a fixed cool white.
    bool moon = Sun.w > 0.5;
    vec3 source = moon ? vec3(0.72, 0.82, 1.0) : texture(InSampler, Sun.xy).rgb;
    float brightness = max(max(source.r, source.g), source.b);
    float activation = moon ? 1.0 : smoothstep(Flare.y, Flare.y + 0.12, brightness);
    if (visibility * activation < 0.001) { fragColor = scene; return; }

    vec2 sun = Sun.xy;
    vec2 fromSun = vec2(0.5) - sun;
    float radial = length((texCoord - sun) * vec2(Detail.w, 1.0));
    float glow = exp(-radial * 16.0) * 0.32 + exp(-radial * 65.0) * 0.32;
    vec3 flare = source * glow * (0.5 + Flare.w);
    for (int i = 0; i < 8; i++) {
        if (i >= int(Flare.z + 0.5)) break;
        float t = 0.55 + float(i) * 1.55 / max(Flare.z - 1.0, 1.0);
        vec2 center = sun + fromSun * t;
        float radius = 0.018 + 0.009 * mod(float(i), 3.0);
        vec2 chroma = normalize(fromSun + vec2(1e-5)) * Detail.x * 0.003;
        vec3 ghost = vec3(disc(texCoord, center + chroma, radius),
                          disc(texCoord, center, radius),
                          disc(texCoord, center - chroma, radius));
        flare += ghost * source * (0.12 / (1.0 + float(i) * 0.55));
    }
    fragColor = vec4(clamp(scene.rgb + flare * Flare.x * visibility * activation, 0.0, 1.0), scene.a);
}
