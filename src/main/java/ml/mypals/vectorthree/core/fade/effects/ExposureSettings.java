package ml.mypals.vectorthree.core.fade.effects;

import java.util.List;

public record ExposureSettings(float target, float compensation, float minEv, float maxEv, float speed) {
    public static final List<String> CHANNELS = List.of("exposure_target", "exposure_compensation",
            "exposure_min", "exposure_max", "exposure_speed");
    public static ExposureSettings defaults() { return new ExposureSettings(0.5f, 0, -2, 2, 2); }
    public ExposureSettings sanitized() {
        float min = Math.clamp(minEv, -8, 8), max = Math.clamp(maxEv, min, 8);
        return new ExposureSettings(Math.clamp(target, 0.05f, 0.95f), Math.clamp(compensation, -4, 4),
                min, max, Math.clamp(speed, 0.05f, 20));
    }
    public ExposureSettings lerp(ExposureSettings to, float t) {
        return new ExposureSettings(mix(target, to.target, t), mix(compensation, to.compensation, t),
                mix(minEv, to.minEv, t), mix(maxEv, to.maxEv, t), mix(speed, to.speed, t));
    }
    public ExposureSettings withChannel(String channel, ExposureSettings source) {
        return new ExposureSettings(channel.equals("exposure_target") ? source.target : target,
                channel.equals("exposure_compensation") ? source.compensation : compensation,
                channel.equals("exposure_min") ? source.minEv : minEv,
                channel.equals("exposure_max") ? source.maxEv : maxEv,
                channel.equals("exposure_speed") ? source.speed : speed);
    }
    public boolean same(ExposureSettings other, String channel) {
        return switch (channel) {
            case "exposure_target" -> target == other.target;
            case "exposure_compensation" -> compensation == other.compensation;
            case "exposure_min" -> minEv == other.minEv;
            case "exposure_max" -> maxEv == other.maxEv;
            case "exposure_speed" -> speed == other.speed;
            default -> true;
        };
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
