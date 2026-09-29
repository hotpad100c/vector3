package ml.mypals.vectorthree.core.fade.effects;

import java.util.List;

public record ReflectionSettings(float intensity, float maxDistance, float thickness, int steps) {
    public static final List<String> CHANNELS = List.of("ssr_intensity", "ssr_distance", "ssr_thickness", "ssr_steps");
    public static ReflectionSettings defaults() { return new ReflectionSettings(0.35f, 12, 0.3f, 24); }
    public ReflectionSettings sanitized() {
        return new ReflectionSettings(Math.clamp(intensity, 0, 1), Math.clamp(maxDistance, 1, 64),
                Math.clamp(thickness, 0.01f, 2), Math.clamp(steps, 4, 48));
    }
    public ReflectionSettings lerp(ReflectionSettings to, float t) {
        return new ReflectionSettings(mix(intensity, to.intensity, t), mix(maxDistance, to.maxDistance, t),
                mix(thickness, to.thickness, t), Math.round(mix(steps, to.steps, t)));
    }
    public ReflectionSettings withChannel(String channel, ReflectionSettings source) {
        return new ReflectionSettings(channel.equals("ssr_intensity") ? source.intensity : intensity,
                channel.equals("ssr_distance") ? source.maxDistance : maxDistance,
                channel.equals("ssr_thickness") ? source.thickness : thickness,
                channel.equals("ssr_steps") ? source.steps : steps);
    }
    public boolean same(ReflectionSettings other, String channel) {
        return switch (channel) {
            case "ssr_intensity" -> intensity == other.intensity;
            case "ssr_distance" -> maxDistance == other.maxDistance;
            case "ssr_thickness" -> thickness == other.thickness;
            case "ssr_steps" -> steps == other.steps;
            default -> true;
        };
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
