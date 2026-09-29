package ml.mypals.vectorthree.core.fade.effects;

import java.util.List;

public record FlareSettings(float intensity, float threshold, int ghosts, float halo, float chromatic) {
    public static final List<String> CHANNELS = List.of("flare_intensity", "flare_threshold", "flare_ghosts",
            "flare_halo", "flare_chromatic");
    public static FlareSettings defaults() { return new FlareSettings(0.35f, 0.8f, 4, 0.4f, 0.2f); }
    public FlareSettings sanitized() {
        return new FlareSettings(Math.clamp(intensity, 0, 3), Math.clamp(threshold, 0, 1),
                Math.clamp(ghosts, 1, 8), Math.clamp(halo, 0, 1), Math.clamp(chromatic, 0, 1));
    }
    public FlareSettings lerp(FlareSettings to, float t) {
        return new FlareSettings(mix(intensity, to.intensity, t), mix(threshold, to.threshold, t),
                Math.round(mix(ghosts, to.ghosts, t)), mix(halo, to.halo, t),
                mix(chromatic, to.chromatic, t));
    }
    public FlareSettings withChannel(String channel, FlareSettings source) {
        return new FlareSettings(channel.equals("flare_intensity") ? source.intensity : intensity,
                channel.equals("flare_threshold") ? source.threshold : threshold,
                channel.equals("flare_ghosts") ? source.ghosts : ghosts,
                channel.equals("flare_halo") ? source.halo : halo,
                channel.equals("flare_chromatic") ? source.chromatic : chromatic);
    }
    public boolean same(FlareSettings other, String channel) {
        return switch (channel) {
            case "flare_intensity" -> intensity == other.intensity;
            case "flare_threshold" -> threshold == other.threshold;
            case "flare_ghosts" -> ghosts == other.ghosts;
            case "flare_halo" -> halo == other.halo;
            case "flare_chromatic" -> chromatic == other.chromatic;
            default -> true;
        };
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
