#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
uniform sampler2D DistanceSampler;
uniform sampler2D MaterialSampler;
layout(std140) uniform ReflectionSettings {
    vec4 Reflection;
    vec4 Screen;
    vec4 CameraLocalPosition;
    vec4 CameraRight;
    vec4 CameraUp;
    vec4 CameraForward;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 viewPosition(vec2 uv, float distance) {
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc * distance / Screen.zw, -distance);
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    float d = texture(DistanceSampler, texCoord).r;
    if (d >= 50000.0 || d < 0.05) { fragColor = scene; return; }
    vec3 p = viewPosition(texCoord, d);
    vec3 worldRay = normalize(CameraRight.xyz * p.x + CameraUp.xyz * p.y - CameraForward.xyz * p.z);
    vec3 world = CameraLocalPosition.xyz + CameraRight.xyz * p.x + CameraUp.xyz * p.y
            - CameraForward.xyz * p.z + worldRay * 0.03;
    ivec3 blockCell = ivec3(floor(world));
    if (any(lessThan(blockCell, ivec3(0))) || any(greaterThanEqual(blockCell, ivec3(48, 64, 48)))) {
        fragColor = scene;
        return;
    }
    if (texelFetch(MaterialSampler, ivec2(blockCell.x + blockCell.z * 48, blockCell.y), 0).r < 0.5) {
        fragColor = scene;
        return;
    }
    vec2 dx = vec2(Screen.x, 0.0), dy = vec2(0.0, Screen.y);
    float depthX = texture(DistanceSampler, clamp(texCoord + dx, vec2(0.0), vec2(1.0))).r;
    float depthY = texture(DistanceSampler, clamp(texCoord + dy, vec2(0.0), vec2(1.0))).r;
    if (abs(depthX - d) > 2.0 || abs(depthY - d) > 2.0) { fragColor = scene; return; }
    vec3 normal = normalize(cross(viewPosition(texCoord + dx, depthX) - p,
                                  viewPosition(texCoord + dy, depthY) - p));
    vec3 view = -normalize(p);
    if (dot(normal, view) < 0.0) normal = -normal;
    vec3 reflected = normalize(reflect(-view, normal));
    float fresnel = 0.15 + 0.85 * pow(1.0 - max(dot(normal, view), 0.0), 5.0);
    vec3 hitColor = scene.rgb;
    float hit = 0.0;
    int count = int(Reflection.w + 0.5);
    for (int i = 0; i < 48; i++) {
        if (i >= count) break;
        float travel = (float(i) + 1.0) * Reflection.y / float(count);
        vec3 q = p + normal * 0.05 + reflected * travel;
        if (q.z > -0.05) break;
        vec2 uv = q.xy / -q.z * Screen.zw * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) break;
        float sceneDepth = texture(DistanceSampler, uv).r;
        float separation = -q.z - sceneDepth;
        if (sceneDepth < 50000.0 && separation > 0.0 && separation < Reflection.z) {
            hitColor = texture(InSampler, uv).rgb;
            hit = (1.0 - travel / Reflection.y) * smoothstep(0.0, 0.08, min(min(uv.x, uv.y),
                  min(1.0 - uv.x, 1.0 - uv.y)));
            break;
        }
    }
    fragColor = vec4(mix(scene.rgb, hitColor, clamp(hit * Reflection.x * fresnel, 0.0, 1.0)), scene.a);
}
