package ml.mypals.vectorthree.fb.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.ReflectionSettings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;

public final class ReflectionEditor {
    private ReflectionEditor() {}
    public static ReflectionSettings edit(ReflectionSettings value) {
        float[] intensity = {value.intensity()}, distance = {value.maxDistance()}, thickness = {value.thickness()};
        int[] steps = {value.steps()};
        ImString materials = new ImString(value.materials(), 1024);
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.ssr.intensity"), intensity, 0, 1);
        changed |= ImGui.sliderFloat(I18n.get("vector3.ssr.distance"), distance, 1, 64);
        changed |= ImGui.sliderFloat(I18n.get("vector3.ssr.thickness"), thickness, 0.01f, 2);
        changed |= ImGui.sliderInt(I18n.get("vector3.ssr.steps"), steps, 4, 48);
        changed |= ImGui.inputText(I18n.get("vector3.ssr.materials"), materials);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.ssr.materials_tip"));
        ImGui.textDisabled(I18n.get("vector3.ssr.limit"));
        return changed ? new ReflectionSettings(intensity[0], distance[0], thickness[0], steps[0],
                materials.get()).sanitized() : value;
    }
}
