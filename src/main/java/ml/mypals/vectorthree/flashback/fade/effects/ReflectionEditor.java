package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class ReflectionEditor {
    private ReflectionEditor() {}
    public static ReflectionSettings edit(ReflectionSettings value) {
        float[] intensity = {value.intensity()}, distance = {value.maxDistance()}, thickness = {value.thickness()};
        int[] steps = {value.steps()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.ssr.intensity"), intensity, 0, 1);
        changed |= ImGui.sliderFloat(I18n.get("vector3.ssr.distance"), distance, 1, 64);
        changed |= ImGui.sliderFloat(I18n.get("vector3.ssr.thickness"), thickness, 0.01f, 2);
        changed |= ImGui.sliderInt(I18n.get("vector3.ssr.steps"), steps, 4, 48);
        ImGui.textDisabled(I18n.get("vector3.ssr.limit"));
        return changed ? new ReflectionSettings(intensity[0], distance[0], thickness[0], steps[0]) : value;
    }
}
