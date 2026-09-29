package ml.mypals.vectorthree.core.fade;

import ml.mypals.vectorthree.core.fade.effects.AdditionalEffects;
import ml.mypals.vectorthree.core.fade.effects.BloomSettings;
import ml.mypals.vectorthree.core.fade.effects.ColorGradingSettings;
import ml.mypals.vectorthree.core.fade.effects.DofSettings;
import ml.mypals.vectorthree.core.fade.effects.GrainSettings;
import ml.mypals.vectorthree.core.fade.effects.LensSettings;
import ml.mypals.vectorthree.core.fade.effects.PixelationSettings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Values on the full-screen effects track. A keyframe holds the settings of every effect and {@code effects} lists
 * which of them run, in order. Version zero is the original Fade save format; {@code effect} is the single-effect
 * field older saves used, kept as the first entry of the list.
 */
public record ScreenVFX(int version, float red, float green, float blue, float opacity,
                        float exposure, float contrast, float saturation, float temperature,
                        float vignette, float vignetteRadius, float dofStrength, float focusDistance,
                        float focusRange, int dofMode, float lutStrength, String lut, String effect,
                        List<String> effects, ColorGradingSettings grading, BloomSettings bloom, DofSettings dof,
                        GrainSettings grain, PixelationSettings pixelation, LensSettings lens,
                        AdditionalEffects additional) {
    public static final int VERSION = 7;
    public static final int DOF_BOTH = 0, DOF_NEAR = 1, DOF_FAR = 2, DOF_DISTANCE = 3;
    public static final String GRADE = "grade", BLOOM = "bloom", DOF = "dof", VIGNETTE = "vignette", LUT = "lut",
            GRAIN = "grain", PIXELATION = "pixelation", LENS = "lens",
            BLUR = "blur", FLARE = "flare", GOD_RAYS = "god_rays", AUTO_EXPOSURE = "auto_exposure", MOTION_BLUR = "motion_blur",
            PANINI = "panini", AO = "ao", SSR = "ssr",
            FADE = "fade", LEGACY = "legacy";
    public static final String[] EFFECTS = {GRADE, BLOOM, DOF, VIGNETTE, LUT, GRAIN, PIXELATION, LENS, BLUR,
            FLARE, GOD_RAYS, AUTO_EXPOSURE, MOTION_BLUR, PANINI, AO, SSR, FADE};

    /** Everything a keyframe holds, for changing a few fields at a time. */
    public static final class Builder {
        public float red, green, blue, opacity, exposure, contrast, saturation, temperature, vignette,
                vignetteRadius, dofStrength, focusDistance, focusRange, lutStrength;
        public int dofMode;
        public String lut;
        public List<String> effects;
        public ColorGradingSettings grading;
        public BloomSettings bloom;
        public DofSettings dof;
        public GrainSettings grain;
        public PixelationSettings pixelation;
        public LensSettings lens;
        public AdditionalEffects additional;

        private Builder(ScreenVFX from) {
            red = from.red; green = from.green; blue = from.blue; opacity = from.opacity;
            exposure = from.exposure; contrast = from.contrast; saturation = from.saturation;
            temperature = from.temperature; vignette = from.vignette; vignetteRadius = from.vignetteRadius;
            dofStrength = from.dofStrength; focusDistance = from.focusDistance; focusRange = from.focusRange;
            dofMode = from.dofMode; lutStrength = from.lutStrength; lut = from.lut; effects = from.effects;
            grading = from.grading; bloom = from.bloom; dof = from.dof; grain = from.grain;
            pixelation = from.pixelation; lens = from.lens; additional = from.additional;
        }

        public ScreenVFX build() {
            return new ScreenVFX(VERSION, red, green, blue, opacity, exposure, contrast, saturation, temperature,
                    vignette, vignetteRadius, dofStrength, focusDistance, focusRange, dofMode, lutStrength, lut,
                    first(effects), List.copyOf(effects), grading, bloom, dof, grain, pixelation, lens, additional);
        }
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static ScreenVFX defaults() {
        return defaults(GRADE);
    }

    public static ScreenVFX defaults(String effect) {
        return new ScreenVFX(VERSION, 0, 0, 0, effect.equals(FADE) ? 1 : 0,
                effect.equals(GRADE) ? 0.25f : 0, 1, 1, 0,
                effect.equals(VIGNETTE) ? 0.4f : 0, 0.75f,
                effect.equals(DOF) ? 0.6f : 0, 10, 2, DOF_BOTH,
                effect.equals(LUT) ? 1 : 0,
                effect.equals(LUT) ? "vector3:textures/lut/warm.png" : "", effect, List.of(effect),
                ColorGradingSettings.defaults(), BloomSettings.defaults(), DofSettings.defaults(),
                GrainSettings.defaults(), PixelationSettings.defaults(), LensSettings.defaults(),
                AdditionalEffects.defaults());
    }

    private static String first(List<String> effects) {
        return effects.isEmpty() ? "" : effects.getFirst();
    }

    /** Older saves: one effect, or the combined keyframe that ran depth of field, grading, vignette and LUT at once. */
    private List<String> migrated() {
        if (LEGACY.equals(effect)) {
            List<String> all = new ArrayList<>();
            if (dofStrength > 0) all.add(DOF);
            all.addAll(List.of(GRADE, VIGNETTE, LUT));
            return all;
        }
        return effect == null || effect.isBlank() ? List.of() : List.of(effect);
    }

    private static List<String> cleaned(List<String> effects) {
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        for (String effect : effects) {
            if (effect != null && java.util.Arrays.asList(EFFECTS).contains(effect)) kept.add(effect);
        }
        return List.copyOf(kept);
    }

    public ScreenVFX sanitized() {
        if (version == 0) {
            Builder fade = defaults(FADE).toBuilder();
            fade.red = red;
            fade.green = green;
            fade.blue = blue;
            fade.opacity = opacity;
            return fade.build();
        }
        List<String> list = cleaned(effects != null ? effects : migrated());
        return new ScreenVFX(VERSION, red, green, blue, opacity, exposure, contrast, saturation,
                temperature, vignette, vignetteRadius, dofStrength,
                Float.isFinite(focusDistance) && focusDistance > 0 ? focusDistance : 10,
                Float.isFinite(focusRange) ? Math.max(0, focusRange) : 2,
                Math.clamp(dofMode, DOF_BOTH, DOF_DISTANCE), lutStrength, lut == null ? "" : lut,
                first(list), list,
                grading == null ? ColorGradingSettings.fromLegacy(exposure, contrast, saturation, temperature)
                        : grading.sanitized(),
                bloom == null ? BloomSettings.defaults() : bloom.sanitized(),
                dof == null ? DofSettings.defaults() : dof.sanitized(),
                grain == null ? GrainSettings.defaults() : grain.sanitized(),
                pixelation == null ? PixelationSettings.defaults() : pixelation.sanitized(),
                lens == null ? LensSettings.defaults() : lens.sanitized(),
                additional == null ? AdditionalEffects.defaults() : additional.sanitized());
    }

    public ScreenVFX lerp(ScreenVFX to, float t) {
        List<String> list = t < 0.5f ? effects : to.effects;
        return new ScreenVFX(VERSION, mix(red, to.red, t), mix(green, to.green, t),
                mix(blue, to.blue, t), mix(opacity, to.opacity, t), mix(exposure, to.exposure, t),
                mix(contrast, to.contrast, t), mix(saturation, to.saturation, t),
                mix(temperature, to.temperature, t), mix(vignette, to.vignette, t),
                mix(vignetteRadius, to.vignetteRadius, t), mix(dofStrength, to.dofStrength, t),
                mix(focusDistance, to.focusDistance, t), mix(focusRange, to.focusRange, t),
                t < 0.5f ? dofMode : to.dofMode, mix(lutStrength, to.lutStrength, t),
                t < 0.5f ? lut : to.lut, first(list), list,
                grading.lerp(to.grading, t), bloom.lerp(to.bloom, t), dof.lerp(to.dof, t),
                grain.lerp(to.grain, t), pixelation.lerp(to.pixelation, t), lens.lerp(to.lens, t),
                additional.lerp(to.additional, t));
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    public ScreenVFX withChannel(String channel, ScreenVFX source) {
        List<String> list = channel.equals("effect") ? source.effects : effects;
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
                first(list), list,
                grading.withChannel(channel, source.grading), bloom.withChannel(channel, source.bloom),
                dof.withChannel(channel, source.dof), grain.withChannel(channel, source.grain),
                pixelation.withChannel(channel, source.pixelation), lens.withChannel(channel, source.lens),
                additional.withChannel(channel, source.additional));
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
            case "effect" -> effects.equals(other.effects);
            default -> grading.same(other.grading, channel) && bloom.same(other.bloom, channel)
                    && dof.same(other.dof, channel) && grain.same(other.grain, channel)
                    && pixelation.same(other.pixelation, channel) && lens.same(other.lens, channel)
                    && additional.same(other.additional, channel);
        };
    }

    public boolean has(String kind) {
        return effects.contains(kind);
    }

    public boolean applies(String kind) {
        return has(kind);
    }

    public ScreenVFX withEffects(List<String> list) {
        Builder builder = toBuilder();
        builder.effects = cleaned(list);
        return builder.build();
    }

    /** Adds an effect at the end of the list, with the starting values a keyframe made for it would have. */
    public ScreenVFX withEffect(String kind) {
        if (has(kind)) return this;
        ScreenVFX start = defaults(kind);
        Builder builder = toBuilder();
        switch (kind) {
            case VIGNETTE -> {
                builder.vignette = start.vignette;
                builder.vignetteRadius = start.vignetteRadius;
            }
            case DOF -> {
                builder.dofStrength = start.dofStrength;
                builder.focusDistance = start.focusDistance;
                builder.focusRange = start.focusRange;
                builder.dofMode = start.dofMode;
            }
            case LUT -> {
                builder.lutStrength = start.lutStrength;
                builder.lut = start.lut;
            }
            case FADE -> {
                builder.red = start.red;
                builder.green = start.green;
                builder.blue = start.blue;
                builder.opacity = start.opacity;
            }
            default -> { }
        }
        List<String> list = new ArrayList<>(effects);
        list.add(kind);
        builder.effects = list;
        return builder.build();
    }

    public ScreenVFX withGrading(ColorGradingSettings value) {
        Builder builder = toBuilder();
        builder.grading = value;
        return builder.build();
    }

    public ScreenVFX withBloom(BloomSettings value) {
        Builder builder = toBuilder();
        builder.bloom = value;
        return builder.build();
    }

    public ScreenVFX withDof(DofSettings value) {
        Builder builder = toBuilder();
        builder.dof = value;
        return builder.build();
    }

    public ScreenVFX withGrain(GrainSettings value) {
        Builder builder = toBuilder();
        builder.grain = value;
        return builder.build();
    }

    public ScreenVFX withPixelation(PixelationSettings value) {
        Builder builder = toBuilder();
        builder.pixelation = value;
        return builder.build();
    }

    public ScreenVFX withLens(LensSettings value) {
        Builder builder = toBuilder();
        builder.lens = value;
        return builder.build();
    }

    public ScreenVFX withAdditional(AdditionalEffects value) {
        Builder builder = toBuilder();
        builder.additional = value;
        return builder.build();
    }
}
