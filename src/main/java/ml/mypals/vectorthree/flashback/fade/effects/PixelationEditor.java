package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class PixelationEditor {
    private PixelationEditor() {}

    public static PixelationSettings edit(PixelationSettings value) {
        float[] block = {value.blockSize()};
        int[] levels = {value.colorLevels()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.pixel.block"), block, 1, 128);
        changed |= ImGui.sliderInt(I18n.get("vector3.pixel.levels"), levels, 2, 256);
        return changed ? new PixelationSettings(block[0], levels[0]) : value;
    }
}
