package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.List;

public record GrainSettings(float intensity, float size, float speed, boolean colored) {
    public static final List<String> CHANNELS = List.of("grain_intensity", "grain_size", "grain_speed", "grain_colored");
    public static GrainSettings defaults() { return new GrainSettings(0.15f, 1.5f, 1, false); }
    public GrainSettings sanitized() {
        return new GrainSettings(Math.clamp(intensity, 0, 1), Math.clamp(size, 1, 16),
                Math.clamp(speed, 0, 10), colored);
    }
    public GrainSettings lerp(GrainSettings to, float t) {
        return new GrainSettings(mix(intensity, to.intensity, t), mix(size, to.size, t),
                mix(speed, to.speed, t), t < 0.5f ? colored : to.colored);
    }
    public GrainSettings withChannel(String channel, GrainSettings source) {
        return new GrainSettings(channel.equals("grain_intensity") ? source.intensity : intensity,
                channel.equals("grain_size") ? source.size : size,
                channel.equals("grain_speed") ? source.speed : speed,
                channel.equals("grain_colored") ? source.colored : colored);
    }
    public boolean same(GrainSettings other, String channel) {
        return switch (channel) {
            case "grain_intensity" -> intensity == other.intensity;
            case "grain_size" -> size == other.size;
            case "grain_speed" -> speed == other.speed;
            case "grain_colored" -> colored == other.colored;
            default -> true;
        };
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
