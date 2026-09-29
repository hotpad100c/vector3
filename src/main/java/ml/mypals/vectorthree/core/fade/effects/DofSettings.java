package ml.mypals.vectorthree.core.fade.effects;

import java.util.List;

/** Lens options of the depth-of-field effect; focus distance, sharp range, blur size and mode live on ScreenVFX. */
public record DofSettings(float aperture, boolean autofocus, float autofocusSmoothing, int shape, float samples,
                          int rings, float rotation, boolean chromatic, float chromaticStrength, boolean anamorphic,
                          boolean fovScaled, float tiltX, float tiltY) {
    public static final List<String> CHANNELS = List.of("dof_aperture", "dof_autofocus", "dof_autofocus_smoothing",
            "dof_shape", "dof_samples", "dof_rings", "dof_rotation", "dof_chromatic", "dof_chromatic_strength",
            "dof_anamorphic",
            "dof_fov_scaled", "dof_tilt_x", "dof_tilt_y");

    public static DofSettings defaults() {
        return new DofSettings(1, false, 0.5f, 0, 3.5f, 5, 0, false, 1, false, false, 0, 0);
    }

    public DofSettings sanitized() {
        return new DofSettings(aperture > 0 ? aperture : 1, autofocus,
                autofocusSmoothing > 0 ? Math.min(autofocusSmoothing, 10) : 0.5f,
                shape < 3 ? 0 : Math.min(shape, 8),
                samples > 0 ? Math.clamp(samples, 1, 5) : 3.5f, rings > 0 ? Math.clamp(rings, 1, 8) : 5,
                rotation, chromatic, chromaticStrength > 0 ? Math.min(chromaticStrength, 8) : 1, anamorphic, fovScaled,
                Math.clamp(tiltX, -20, 20), Math.clamp(tiltY, -20, 20));
    }

    public DofSettings lerp(DofSettings to, float t) {
        boolean first = t < 0.5f;
        return new DofSettings(mix(aperture, to.aperture, t), first ? autofocus : to.autofocus,
                mix(autofocusSmoothing, to.autofocusSmoothing, t), first ? shape : to.shape,
                mix(samples, to.samples, t), first ? rings : to.rings,
                mix(rotation, to.rotation, t), first ? chromatic : to.chromatic,
                mix(chromaticStrength, to.chromaticStrength, t), first ? anamorphic : to.anamorphic,
                first ? fovScaled : to.fovScaled, mix(tiltX, to.tiltX, t), mix(tiltY, to.tiltY, t));
    }

    public DofSettings withChannel(String channel, DofSettings source) {
        return new DofSettings(
                channel.equals("dof_aperture") ? source.aperture : aperture,
                channel.equals("dof_autofocus") ? source.autofocus : autofocus,
                channel.equals("dof_autofocus_smoothing") ? source.autofocusSmoothing : autofocusSmoothing,
                channel.equals("dof_shape") ? source.shape : shape,
                channel.equals("dof_samples") ? source.samples : samples,
                channel.equals("dof_rings") ? source.rings : rings,
                channel.equals("dof_rotation") ? source.rotation : rotation,
                channel.equals("dof_chromatic") ? source.chromatic : chromatic,
                channel.equals("dof_chromatic_strength") ? source.chromaticStrength : chromaticStrength,
                channel.equals("dof_anamorphic") ? source.anamorphic : anamorphic,
                channel.equals("dof_fov_scaled") ? source.fovScaled : fovScaled,
                channel.equals("dof_tilt_x") ? source.tiltX : tiltX,
                channel.equals("dof_tilt_y") ? source.tiltY : tiltY);
    }

    public boolean same(DofSettings other, String channel) {
        return switch (channel) {
            case "dof_aperture" -> aperture == other.aperture;
            case "dof_autofocus" -> autofocus == other.autofocus;
            case "dof_autofocus_smoothing" -> autofocusSmoothing == other.autofocusSmoothing;
            case "dof_shape" -> shape == other.shape;
            case "dof_samples" -> samples == other.samples;
            case "dof_rings" -> rings == other.rings;
            case "dof_rotation" -> rotation == other.rotation;
            case "dof_chromatic" -> chromatic == other.chromatic;
            case "dof_chromatic_strength" -> chromaticStrength == other.chromaticStrength;
            case "dof_anamorphic" -> anamorphic == other.anamorphic;
            case "dof_fov_scaled" -> fovScaled == other.fovScaled;
            case "dof_tilt_x" -> tiltX == other.tiltX;
            case "dof_tilt_y" -> tiltY == other.tiltY;
            default -> true;
        };
    }

    /** Focus distance at a screen position, matching the shaders' tilt. */
    public double focusAt(double focus, double u, double v) {
        return focus * Math.pow(2, (tiltX * (u * 2 - 1) + tiltY * (v * 2 - 1)) * 0.1);
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
