package ml.mypals.vectorthree.fb.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.GrainSettings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import net.minecraft.client.resources.language.I18n;

public final class GrainEditor {
    private GrainEditor() {}

    public static GrainSettings edit(GrainSettings value) {
        float[] intensity = {value.intensity()}, size = {value.size()}, speed = {value.speed()};
        ImBoolean colored = new ImBoolean(value.colored());
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.grain.intensity"), intensity, 0, 1);
        changed |= ImGui.sliderFloat(I18n.get("vector3.grain.size"), size, 1, 16);
        changed |= ImGui.sliderFloat(I18n.get("vector3.grain.speed"), speed, 0, 10);
        changed |= ImGui.checkbox(I18n.get("vector3.grain.colored"), colored);
        return changed ? new GrainSettings(intensity[0], size[0], speed[0], colored.get()) : value;
    }
}
