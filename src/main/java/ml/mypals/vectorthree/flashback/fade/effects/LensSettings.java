package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.List;

public record LensSettings(float distortion, float chromatic, float centerX, float centerY) {
    public static final List<String> CHANNELS = List.of("lens_distortion", "lens_chromatic", "lens_center_x", "lens_center_y");
    public static LensSettings defaults() { return new LensSettings(0.12f, 0.25f, 0.5f, 0.5f); }
    public LensSettings sanitized() {
        return new LensSettings(Math.clamp(distortion, -1, 1), Math.clamp(chromatic, 0, 1),
                Math.clamp(centerX, 0, 1), Math.clamp(centerY, 0, 1));
    }
    public LensSettings lerp(LensSettings to, float t) {
        return new LensSettings(mix(distortion, to.distortion, t), mix(chromatic, to.chromatic, t),
                mix(centerX, to.centerX, t), mix(centerY, to.centerY, t));
    }
    public LensSettings withChannel(String channel, LensSettings source) {
        return new LensSettings(channel.equals("lens_distortion") ? source.distortion : distortion,
                channel.equals("lens_chromatic") ? source.chromatic : chromatic,
                channel.equals("lens_center_x") ? source.centerX : centerX,
                channel.equals("lens_center_y") ? source.centerY : centerY);
    }
    public boolean same(LensSettings other, String channel) {
        return switch (channel) {
            case "lens_distortion" -> distortion == other.distortion;
            case "lens_chromatic" -> chromatic == other.chromatic;
            case "lens_center_x" -> centerX == other.centerX;
            case "lens_center_y" -> centerY == other.centerY;
            default -> true;
        };
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
