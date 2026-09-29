package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class BlurEditor {
    private BlurEditor() {}

    public static BlurSettings edit(BlurSettings value) {
        float[] radius = {value.radius()}, amount = {value.amount()}, clear = {value.clear()};
        float[] feather = {value.feather()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.blur.radius"), radius, 0, 100, "%.1f");
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.blur.radius_tip"));
        changed |= ImGui.sliderFloat(I18n.get("vector3.blur.amount"), amount, 0, 1);
        changed |= ImGui.sliderFloat(I18n.get("vector3.blur.clear"), clear, 0, 2);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.blur.clear_tip"));
        if (clear[0] > 0) changed |= ImGui.sliderFloat(I18n.get("vector3.blur.feather"), feather, 0, 2);
        return changed ? new BlurSettings(radius[0], amount[0], clear[0], feather[0]).sanitized() : value;
    }
}
