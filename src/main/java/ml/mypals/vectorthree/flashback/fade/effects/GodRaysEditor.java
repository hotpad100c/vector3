package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import ml.mypals.vectorthree.flashback.fade.effects.ColorGradingSettings.Rgb;
import net.minecraft.client.resources.language.I18n;

public final class GodRaysEditor {
    private GodRaysEditor() {}

    public static GodRaysSettings edit(GodRaysSettings value) {
        float[] intensity = {value.intensity()}, length = {value.length()}, decay = {value.decay()};
        float[] falloff = {value.falloff()}, threshold = {value.threshold()};
        float[] tint = {value.tint().r(), value.tint().g(), value.tint().b()};
        int[] samples = {value.samples()};
        ImBoolean sky = new ImBoolean(value.sky()), bright = new ImBoolean(value.bright());
        ImGui.textDisabled(I18n.get("vector3.rays.note"));
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.rays.intensity"), intensity, 0, 4);
        changed |= ImGui.sliderFloat(I18n.get("vector3.rays.length"), length, 0.05f, 1.5f);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.rays.length_tip"));
        changed |= ImGui.sliderFloat(I18n.get("vector3.rays.decay"), decay, 0.8f, 1, "%.3f");
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.rays.decay_tip"));
        changed |= ImGui.sliderFloat(I18n.get("vector3.rays.falloff"), falloff, 0, 4);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.rays.falloff_tip"));
        changed |= ImGui.sliderInt(I18n.get("vector3.rays.samples"), samples, 8, 128);
        changed |= ImGui.colorEdit3(I18n.get("vector3.rays.tint"), tint);
        changed |= ImGui.checkbox(I18n.get("vector3.rays.sky"), sky);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.rays.sky_tip"));
        changed |= ImGui.checkbox(I18n.get("vector3.rays.bright"), bright);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.rays.bright_tip"));
        if (bright.get()) changed |= ImGui.sliderFloat(I18n.get("vector3.rays.threshold"), threshold, 0, 1);
        return changed ? new GodRaysSettings(intensity[0], length[0], decay[0], samples[0],
                new Rgb(tint[0], tint[1], tint[2]), falloff[0], sky.get(), bright.get(), threshold[0]).sanitized()
                : value;
    }
}
