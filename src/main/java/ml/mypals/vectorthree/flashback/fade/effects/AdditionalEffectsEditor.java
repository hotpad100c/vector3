package ml.mypals.vectorthree.flashback.fade.effects;

import ml.mypals.vectorthree.flashback.fade.ScreenVFX;

public final class AdditionalEffectsEditor {
    private AdditionalEffectsEditor() {}

    public static AdditionalEffects edit(String effect, AdditionalEffects value) {
        switch (effect) {
            case ScreenVFX.FLARE -> {
                FlareSettings edited = FlareEditor.edit(value.flare());
                return edited == value.flare() ? value : value.withFlare(edited);
            }
            case ScreenVFX.AUTO_EXPOSURE -> {
                ExposureSettings edited = ExposureEditor.edit(value.exposure());
                return edited == value.exposure() ? value : value.withExposure(edited);
            }
            case ScreenVFX.MOTION_BLUR -> {
                MotionBlurSettings edited = MotionBlurEditor.edit(value.motion());
                return edited == value.motion() ? value : value.withMotion(edited);
            }
            case ScreenVFX.PANINI -> {
                PaniniSettings edited = PaniniEditor.edit(value.panini());
                return edited == value.panini() ? value : value.withPanini(edited);
            }
            case ScreenVFX.AO -> {
                OcclusionSettings edited = OcclusionEditor.edit(value.ao());
                return edited == value.ao() ? value : value.withAo(edited);
            }
            case ScreenVFX.SSR -> {
                ReflectionSettings edited = ReflectionEditor.edit(value.ssr());
                return edited == value.ssr() ? value : value.withSsr(edited);
            }
            default -> { return value; }
        }
    }
}
