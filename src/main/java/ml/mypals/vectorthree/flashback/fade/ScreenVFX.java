package ml.mypals.vectorthree.flashback.fade;

import ml.mypals.vectorthree.flashback.fade.effects.BloomSettings;
import ml.mypals.vectorthree.flashback.fade.effects.ColorGradingSettings;

/** Values on the full-screen effects track. Version zero is the original Fade save format. */
public record ScreenVFX(int version, float red, float green, float blue, float opacity,
                        float exposure, float contrast, float saturation, float temperature,
                        float vignette, float vignetteRadius, float dofStrength, float focusDistance,
                        float focusRange, int dofMode, float lutStrength, String lut, String effect,
                        ColorGradingSettings grading, BloomSettings bloom) {
    public static final int VERSION = 4;
    public static final int DOF_BOTH = 0, DOF_NEAR = 1, DOF_FAR = 2;
    public static final String GRADE = "grade", BLOOM = "bloom", DOF = "dof", VIGNETTE = "vignette", LUT = "lut",
            FADE = "fade", LEGACY = "legacy";
    public static final String[] EFFECTS = {GRADE, BLOOM, DOF, VIGNETTE, LUT, FADE};

    public static ScreenVFX defaults() {
        return defaults(GRADE);
    }

    public static ScreenVFX defaults(String effect) {
        return new ScreenVFX(VERSION, 0, 0, 0, effect.equals(FADE) ? 1 : 0,
                effect.equals(GRADE) ? 0.25f : 0, 1, 1, 0,
                effect.equals(VIGNETTE) ? 0.4f : 0, 0.75f,
                effect.equals(DOF) ? 0.6f : 0, 10, 2, DOF_BOTH,
                effect.equals(LUT) ? 1 : 0,
                effect.equals(LUT) ? "vector3:textures/lut/warm.png" : "", effect,
                ColorGradingSettings.defaults(), BloomSettings.defaults());
    }

    public ScreenVFX sanitized() {
        if (version == 0) return new ScreenVFX(VERSION, red, green, blue, opacity, 0, 1, 1, 0,
                0, 0.75f, 0, 10, 2, DOF_BOTH, 0, "", FADE,
                ColorGradingSettings.defaults(), BloomSettings.defaults());
        return new ScreenVFX(VERSION, red, green, blue, opacity, exposure, contrast, saturation,
                temperature, vignette, vignetteRadius, dofStrength,
                Float.isFinite(focusDistance) && focusDistance > 0 ? focusDistance : 10,
                Float.isFinite(focusRange) ? Math.max(0, focusRange) : 2,
                Math.clamp(dofMode, DOF_BOTH, DOF_FAR), lutStrength, lut == null ? "" : lut,
                effect == null ? LEGACY : effect,
                version < VERSION || grading == null
                        ? ColorGradingSettings.fromLegacy(exposure, contrast, saturation, temperature)
                        : grading.sanitized(),
                bloom == null ? BloomSettings.defaults() : bloom.sanitized());
    }

    public ScreenVFX lerp(ScreenVFX to, float t) {
        return new ScreenVFX(VERSION, mix(red, to.red, t), mix(green, to.green, t),
                mix(blue, to.blue, t), mix(opacity, to.opacity, t), mix(exposure, to.exposure, t),
                mix(contrast, to.contrast, t), mix(saturation, to.saturation, t),
                mix(temperature, to.temperature, t), mix(vignette, to.vignette, t),
                mix(vignetteRadius, to.vignetteRadius, t), mix(dofStrength, to.dofStrength, t),
                mix(focusDistance, to.focusDistance, t), mix(focusRange, to.focusRange, t),
                t < 0.5f ? dofMode : to.dofMode, mix(lutStrength, to.lutStrength, t),
                t < 0.5f ? lut : to.lut, t < 0.5f ? effect : to.effect,
                grading.lerp(to.grading, t), bloom.lerp(to.bloom, t));
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    public ScreenVFX withChannel(String channel, ScreenVFX source) {
        return new ScreenVFX(VERSION,
                channel.equals("colour") ? source.red : red,
                channel.equals("colour") ? source.green : green,
                channel.equals("colour") ? source.blue : blue,
                channel.equals("opacity") ? source.opacity : opacity,
                channel.equals("exposure") ? source.exposure : exposure,
                channel.equals("contrast") ? source.contrast : contrast,
                channel.equals("saturation") ? source.saturation : saturation,
                channel.equals("temperature") ? source.temperature : temperature,
                channel.equals("vignette") ? source.vignette : vignette,
                channel.equals("vignette_radius") ? source.vignetteRadius : vignetteRadius,
                channel.equals("dof") ? source.dofStrength : dofStrength,
                channel.equals("focus_depth") ? source.focusDistance : focusDistance,
                channel.equals("focus_range") ? source.focusRange : focusRange,
                channel.equals("dof_mode") ? source.dofMode : dofMode,
                channel.equals("lut_strength") ? source.lutStrength : lutStrength,
                channel.equals("lut") ? source.lut : lut,
                channel.equals("effect") ? source.effect : effect,
                grading.withChannel(channel, source.grading), bloom.withChannel(channel, source.bloom));
    }

    public boolean same(ScreenVFX other, String channel) {
        return switch (channel) {
            case "colour" -> red == other.red && green == other.green && blue == other.blue;
            case "opacity" -> opacity == other.opacity;
            case "exposure", "contrast", "saturation", "temperature" -> grading.same(other.grading, channel);
            case "vignette" -> vignette == other.vignette;
            case "vignette_radius" -> vignetteRadius == other.vignetteRadius;
            case "dof" -> dofStrength == other.dofStrength;
            case "focus_depth" -> focusDistance == other.focusDistance;
            case "focus_range" -> focusRange == other.focusRange;
            case "dof_mode" -> dofMode == other.dofMode;
            case "lut_strength" -> lutStrength == other.lutStrength;
            case "lut" -> lut.equals(other.lut);
            case "effect" -> effect.equals(other.effect);
            default -> grading.same(other.grading, channel) && bloom.same(other.bloom, channel);
        };
    }

    public boolean applies(String kind) {
        return effect.equals(kind) || effect.equals(LEGACY);
    }

    public ScreenVFX withGrading(ColorGradingSettings value) {
        return new ScreenVFX(VERSION, red, green, blue, opacity, exposure, contrast, saturation, temperature,
                vignette, vignetteRadius, dofStrength, focusDistance, focusRange, dofMode,
                lutStrength, lut, effect, value, bloom);
    }

    public ScreenVFX withBloom(BloomSettings value) {
        return new ScreenVFX(VERSION, red, green, blue, opacity, exposure, contrast, saturation, temperature,
                vignette, vignetteRadius, dofStrength, focusDistance, focusRange, dofMode,
                lutStrength, lut, effect, grading, value);
    }
}
