package ml.mypals.vectorthree.fb.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.PaniniSettings;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class PaniniEditor {
    private PaniniEditor() {}
    public static PaniniSettings edit(PaniniSettings value) {
        float[] distance = {value.distance()}, crop = {value.crop()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.panini.distance"), distance, 0, 1);
        changed |= ImGui.sliderFloat(I18n.get("vector3.panini.crop"), crop, 0, 1);
        return changed ? new PaniniSettings(distance[0], crop[0]) : value;
    }
}
