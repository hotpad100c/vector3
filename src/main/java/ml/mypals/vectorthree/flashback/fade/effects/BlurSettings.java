package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.List;

/** Gaussian blur of the whole frame; {@code clear} keeps a sharp area in the middle. */
public record BlurSettings(float radius, float amount, float clear, float feather) {
    public static final List<String> CHANNELS = List.of("blur_radius", "blur_amount", "blur_clear", "blur_feather");

    public static BlurSettings defaults() {
        return new BlurSettings(12, 1, 0, 0.3f);
    }

    public BlurSettings sanitized() {
        return new BlurSettings(Math.clamp(radius, 0, 300), Math.clamp(amount, 0, 1), Math.clamp(clear, 0, 2),
                Math.clamp(feather, 0, 2));
    }

    public BlurSettings lerp(BlurSettings to, float t) {
        return new BlurSettings(mix(radius, to.radius, t), mix(amount, to.amount, t), mix(clear, to.clear, t),
                mix(feather, to.feather, t));
    }

    public BlurSettings withChannel(String channel, BlurSettings source) {
        return new BlurSettings(channel.equals("blur_radius") ? source.radius : radius,
                channel.equals("blur_amount") ? source.amount : amount,
                channel.equals("blur_clear") ? source.clear : clear,
                channel.equals("blur_feather") ? source.feather : feather);
    }

    public boolean same(BlurSettings other, String channel) {
        return switch (channel) {
            case "blur_radius" -> radius == other.radius;
            case "blur_amount" -> amount == other.amount;
            case "blur_clear" -> clear == other.clear;
            case "blur_feather" -> feather == other.feather;
            default -> true;
        };
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
