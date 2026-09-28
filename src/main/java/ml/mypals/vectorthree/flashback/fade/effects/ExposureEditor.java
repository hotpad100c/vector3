package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class ExposureEditor {
    private ExposureEditor() {}
    public static ExposureSettings edit(ExposureSettings value) {
        float[] target = {value.target()}, compensation = {value.compensation()}, min = {value.minEv()};
        float[] max = {value.maxEv()}, speed = {value.speed()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.autoexposure.target"), target, 0.05f, 0.95f);
        changed |= ImGui.sliderFloat(I18n.get("vector3.autoexposure.compensation"), compensation, -4, 4);
        changed |= ImGui.sliderFloat(I18n.get("vector3.autoexposure.min"), min, -8, 8);
        changed |= ImGui.sliderFloat(I18n.get("vector3.autoexposure.max"), max, -8, 8);
        changed |= ImGui.sliderFloat(I18n.get("vector3.autoexposure.speed"), speed, 0.05f, 20);
        return changed ? new ExposureSettings(target[0], compensation[0], min[0], max[0], speed[0]).sanitized() : value;
    }
}
