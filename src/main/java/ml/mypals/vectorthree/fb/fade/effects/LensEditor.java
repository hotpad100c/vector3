package ml.mypals.vectorthree.fb.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.LensSettings;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class LensEditor {
    private LensEditor() {}

    public static LensSettings edit(LensSettings value) {
        float[] distortion = {value.distortion()}, chromatic = {value.chromatic()};
        float[] center = {value.centerX(), value.centerY()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.lens.distortion"), distortion, -1, 1);
        changed |= ImGui.sliderFloat(I18n.get("vector3.lens.chromatic"), chromatic, 0, 1);
        changed |= ImGui.sliderFloat2(I18n.get("vector3.lens.center"), center, 0, 1);
        return changed ? new LensSettings(distortion[0], chromatic[0], center[0], center[1]) : value;
    }
}
