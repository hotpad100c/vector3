#version 330
uniform sampler2D InSampler;
uniform sampler2D BlurSampler;
layout(std140) uniform BlurComposite {
    vec4 Mix; // amount, radius of the sharp centre, its feather, aspect ratio
};
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec3 blurred = texture(BlurSampler, texCoord).rgb;
    float amount = Mix.x;
    if (Mix.y > 0.0) {
        // 1 at the middle of the top and bottom edges, so the sharp area is a circle in screen shape.
        float dist = length((texCoord - 0.5) * vec2(Mix.w, 1.0)) * 2.0;
        amount *= smoothstep(Mix.y, Mix.y + max(Mix.z, 0.001), dist);
    }
    fragColor = vec4(mix(scene.rgb, blurred, amount), scene.a);
}
