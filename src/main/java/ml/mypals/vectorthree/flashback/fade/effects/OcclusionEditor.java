package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class OcclusionEditor {
    private OcclusionEditor() {}
    public static OcclusionSettings edit(OcclusionSettings value) {
        float[] intensity = {value.intensity()}, radius = {value.radius()}, bias = {value.bias()};
        int[] samples = {value.samples()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.ao.intensity"), intensity, 0, 3);
        changed |= ImGui.sliderFloat(I18n.get("vector3.ao.radius"), radius, 0.05f, 8);
        changed |= ImGui.sliderFloat(I18n.get("vector3.ao.bias"), bias, 0, 1);
        changed |= ImGui.sliderInt(I18n.get("vector3.ao.samples"), samples, 4, 32);
        ImGui.textDisabled(I18n.get("vector3.ao.limit"));
        return changed ? new OcclusionSettings(intensity[0], radius[0], bias[0], samples[0]) : value;
    }
}
