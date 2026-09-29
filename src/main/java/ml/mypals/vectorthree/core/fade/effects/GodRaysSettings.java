package ml.mypals.vectorthree.core.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.ColorGradingSettings.Rgb;

import java.util.List;

/** Light shafts from the sun: what emits light, how far the rays reach, and how they fade. */
public record GodRaysSettings(float intensity, float length, float decay, int samples, Rgb tint, float falloff,
                              boolean sky, boolean bright, float threshold) {
    public static final List<String> CHANNELS = List.of("rays_intensity", "rays_length", "rays_decay", "rays_samples",
            "rays_tint", "rays_falloff", "rays_sky", "rays_bright", "rays_threshold");

    public static GodRaysSettings defaults() {
        return new GodRaysSettings(0.6f, 0.9f, 0.96f, 64, new Rgb(1, 0.92f, 0.72f), 0.5f, true, true, 0.9f);
    }

    public GodRaysSettings sanitized() {
        return new GodRaysSettings(Math.clamp(intensity, 0, 4), Math.clamp(length, 0.05f, 1.5f),
                Math.clamp(decay, 0.8f, 1), Math.clamp(samples, 8, 128), tint == null ? Rgb.ONE : tint,
                Math.clamp(falloff, 0, 4), sky, bright, Math.clamp(threshold, 0, 1));
    }

    public GodRaysSettings lerp(GodRaysSettings to, float t) {
        boolean first = t < 0.5f;
        return new GodRaysSettings(mix(intensity, to.intensity, t), mix(length, to.length, t),
                mix(decay, to.decay, t), Math.round(mix(samples, to.samples, t)), tint.lerp(to.tint, t),
                mix(falloff, to.falloff, t), first ? sky : to.sky, first ? bright : to.bright,
                mix(threshold, to.threshold, t));
    }

    public GodRaysSettings withChannel(String channel, GodRaysSettings source) {
        return new GodRaysSettings(channel.equals("rays_intensity") ? source.intensity : intensity,
                channel.equals("rays_length") ? source.length : length,
                channel.equals("rays_decay") ? source.decay : decay,
                channel.equals("rays_samples") ? source.samples : samples,
                channel.equals("rays_tint") ? source.tint : tint,
                channel.equals("rays_falloff") ? source.falloff : falloff,
                channel.equals("rays_sky") ? source.sky : sky,
                channel.equals("rays_bright") ? source.bright : bright,
                channel.equals("rays_threshold") ? source.threshold : threshold);
    }

    public boolean same(GodRaysSettings other, String channel) {
        return switch (channel) {
            case "rays_intensity" -> intensity == other.intensity;
            case "rays_length" -> length == other.length;
            case "rays_decay" -> decay == other.decay;
            case "rays_samples" -> samples == other.samples;
            case "rays_tint" -> tint.equals(other.tint);
            case "rays_falloff" -> falloff == other.falloff;
            case "rays_sky" -> sky == other.sky;
            case "rays_bright" -> bright == other.bright;
            case "rays_threshold" -> threshold == other.threshold;
            default -> true;
        };
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
