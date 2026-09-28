package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.List;

public record OcclusionSettings(float intensity, float radius, float bias, int samples) {
    public static final List<String> CHANNELS = List.of("ao_intensity", "ao_radius", "ao_bias", "ao_samples");
    public static OcclusionSettings defaults() { return new OcclusionSettings(0.7f, 1.2f, 0.1f, 16); }
    public OcclusionSettings sanitized() {
        return new OcclusionSettings(Math.clamp(intensity, 0, 3), Math.clamp(radius, 0.05f, 8),
                Math.clamp(bias, 0, 1), Math.clamp(samples, 4, 32));
    }
    public OcclusionSettings lerp(OcclusionSettings to, float t) {
        return new OcclusionSettings(mix(intensity, to.intensity, t), mix(radius, to.radius, t),
                mix(bias, to.bias, t), Math.round(mix(samples, to.samples, t)));
    }
    public OcclusionSettings withChannel(String channel, OcclusionSettings source) {
        return new OcclusionSettings(channel.equals("ao_intensity") ? source.intensity : intensity,
                channel.equals("ao_radius") ? source.radius : radius,
                channel.equals("ao_bias") ? source.bias : bias,
                channel.equals("ao_samples") ? source.samples : samples);
    }
    public boolean same(OcclusionSettings other, String channel) {
        return switch (channel) {
            case "ao_intensity" -> intensity == other.intensity;
            case "ao_radius" -> radius == other.radius;
            case "ao_bias" -> bias == other.bias;
            case "ao_samples" -> samples == other.samples;
            default -> true;
        };
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
