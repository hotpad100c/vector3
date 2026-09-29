package ml.mypals.vectorthree.core.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.ColorGradingSettings.Rgb;

public record BloomSettings(float intensity, float threshold, float softKnee, float radius,
                            float clamp, boolean antiFlicker, boolean highQuality,
                            Rgb tint, String dirtTexture, float dirtIntensity) {
    public static BloomSettings defaults() {
        return new BloomSettings(0.6f, 0.8f, 0.5f, 0.7f, 1, false, true,
                Rgb.ONE, "", 0);
    }

    public BloomSettings sanitized() {
        return new BloomSettings(Math.max(0, intensity), Math.max(0, threshold),
                Math.clamp(softKnee, 0, 1), Math.clamp(radius, 0, 1), Math.max(0.01f, clamp),
                antiFlicker, highQuality, tint == null ? Rgb.ONE : tint,
                dirtTexture == null ? "" : dirtTexture, Math.max(0, dirtIntensity));
    }

    public BloomSettings lerp(BloomSettings to, float t) {
        return new BloomSettings(mix(intensity, to.intensity, t), mix(threshold, to.threshold, t),
                mix(softKnee, to.softKnee, t), mix(radius, to.radius, t), mix(clamp, to.clamp, t),
                t < 0.5f ? antiFlicker : to.antiFlicker,
                t < 0.5f ? highQuality : to.highQuality,
                tint.lerp(to.tint, t), t < 0.5f ? dirtTexture : to.dirtTexture,
                mix(dirtIntensity, to.dirtIntensity, t));
    }

    public BloomSettings withChannel(String channel, BloomSettings source) {
        return new BloomSettings(
                channel.equals("bloom_intensity") ? source.intensity : intensity,
                channel.equals("bloom_threshold") ? source.threshold : threshold,
                channel.equals("bloom_soft_knee") ? source.softKnee : softKnee,
                channel.equals("bloom_radius") ? source.radius : radius,
                channel.equals("bloom_clamp") ? source.clamp : clamp,
                channel.equals("bloom_anti_flicker") ? source.antiFlicker : antiFlicker,
                channel.equals("bloom_high_quality") ? source.highQuality : highQuality,
                channel.equals("bloom_tint") ? source.tint : tint,
                channel.equals("bloom_dirt_texture") ? source.dirtTexture : dirtTexture,
                channel.equals("bloom_dirt_intensity") ? source.dirtIntensity : dirtIntensity);
    }

    public boolean same(BloomSettings other, String channel) {
        return switch (channel) {
            case "bloom_intensity" -> intensity == other.intensity;
            case "bloom_threshold" -> threshold == other.threshold;
            case "bloom_soft_knee" -> softKnee == other.softKnee;
            case "bloom_radius" -> radius == other.radius;
            case "bloom_clamp" -> clamp == other.clamp;
            case "bloom_anti_flicker" -> antiFlicker == other.antiFlicker;
            case "bloom_high_quality" -> highQuality == other.highQuality;
            case "bloom_tint" -> tint.equals(other.tint);
            case "bloom_dirt_texture" -> dirtTexture.equals(other.dirtTexture);
            case "bloom_dirt_intensity" -> dirtIntensity == other.dirtIntensity;
            default -> true;
        };
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
