package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import net.minecraft.client.resources.language.I18n;

import static ml.mypals.vectorthree.flashback.fade.effects.ColorGradingSettings.Rgb;

public final class ColorGradingEditor {
    private ColorGradingEditor() {}

    public static ColorGradingSettings edit(ColorGradingSettings value) {
        float[] exposure = {value.exposure()}, temperature = {value.temperature()}, tint = {value.tint()};
        float[] hue = {value.hue()}, saturation = {value.saturation()}, contrast = {value.contrast()};
        float[] red = rgb(value.mixerRed()), green = rgb(value.mixerGreen()), blue = rgb(value.mixerBlue());
        float[] lift = rgb(value.lift()), gamma = rgb(value.gamma()), gain = rgb(value.gain());
        ImInt toneMap = new ImInt(value.toneMap());
        boolean changed = false;
        if (ImGui.collapsingHeader(I18n.get("vector3.grade.basic"))) {
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.exposure"), exposure, -4, 4);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.temperature"), temperature, -100, 100);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.tint"), tint, -100, 100);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.hue"), hue, -180, 180);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.saturation"), saturation, -100, 100);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.contrast"), contrast, -100, 100);
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.grade.tonemapping"))) {
            changed |= ImGui.combo(I18n.get("vector3.grade.tonemap"), toneMap, new String[]{
                    I18n.get("vector3.grade.tonemap.none"), I18n.get("vector3.grade.tonemap.neutral"),
                    I18n.get("vector3.grade.tonemap.aces")});
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.grade.channel_mixer"))) {
            changed |= ImGui.dragFloat3(I18n.get("vector3.grade.mixer_red"), red, 0.01f, -2, 2);
            changed |= ImGui.dragFloat3(I18n.get("vector3.grade.mixer_green"), green, 0.01f, -2, 2);
            changed |= ImGui.dragFloat3(I18n.get("vector3.grade.mixer_blue"), blue, 0.01f, -2, 2);
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.grade.lift_gamma_gain"))) {
            Rgb wheelLift = ColorWheelEditor.edit(I18n.get("vector3.grade.lift"), rgb(lift), -1, 1);
            Rgb wheelGamma = ColorWheelEditor.edit(I18n.get("vector3.grade.gamma"), rgb(gamma), 0.1f, 3);
            Rgb wheelGain = ColorWheelEditor.edit(I18n.get("vector3.grade.gain"), rgb(gain), 0, 3);
            if (!wheelLift.equals(rgb(lift))) { lift = rgb(wheelLift); changed = true; }
            if (!wheelGamma.equals(rgb(gamma))) { gamma = rgb(wheelGamma); changed = true; }
            if (!wheelGain.equals(rgb(gain))) { gain = rgb(wheelGain); changed = true; }
        }
        GradingCurves curves = GradingCurvesEditor.edit(value.curves());
        AdvancedGradingSettings advanced = AdvancedGradingEditor.edit(value.advanced());
        return changed ? new ColorGradingSettings(exposure[0], temperature[0], tint[0], hue[0],
                saturation[0], contrast[0], toneMap.get(), rgb(red), rgb(green), rgb(blue),
                rgb(lift), rgb(gamma), rgb(gain), curves, advanced)
                : curves != value.curves() || advanced != value.advanced()
                ? new ColorGradingSettings(value.exposure(), value.temperature(), value.tint(),
                value.hue(), value.saturation(), value.contrast(), value.toneMap(), value.mixerRed(), value.mixerGreen(),
                value.mixerBlue(), value.lift(), value.gamma(), value.gain(), curves, advanced) : value;
    }

    private static float[] rgb(Rgb value) { return new float[]{value.r(), value.g(), value.b()}; }
    private static Rgb rgb(float[] value) { return new Rgb(value[0], value[1], value[2]); }
}
