package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.List;

public record MotionBlurSettings(float strength, int samples, float maxPixels) {
    public static final List<String> CHANNELS = List.of("motion_strength", "motion_samples", "motion_max_pixels");
    public static MotionBlurSettings defaults() { return new MotionBlurSettings(0.5f, 12, 32); }
    public MotionBlurSettings sanitized() {
        return new MotionBlurSettings(Math.clamp(strength, 0, 2), Math.clamp(samples, 2, 32),
                Math.clamp(maxPixels, 1, 128));
    }
    public MotionBlurSettings lerp(MotionBlurSettings to, float t) {
        return new MotionBlurSettings(strength + (to.strength - strength) * t,
                Math.round(samples + (to.samples - samples) * t), maxPixels + (to.maxPixels - maxPixels) * t);
    }
    public MotionBlurSettings withChannel(String channel, MotionBlurSettings source) {
        return new MotionBlurSettings(channel.equals("motion_strength") ? source.strength : strength,
                channel.equals("motion_samples") ? source.samples : samples,
                channel.equals("motion_max_pixels") ? source.maxPixels : maxPixels);
    }
    public boolean same(MotionBlurSettings other, String channel) {
        return switch (channel) {
            case "motion_strength" -> strength == other.strength;
            case "motion_samples" -> samples == other.samples;
            case "motion_max_pixels" -> maxPixels == other.maxPixels;
            default -> true;
        };
    }
}
