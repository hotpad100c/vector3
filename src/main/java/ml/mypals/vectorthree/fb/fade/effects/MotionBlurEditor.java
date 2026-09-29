package ml.mypals.vectorthree.fb.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.MotionBlurSettings;

import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

public final class MotionBlurEditor {
    private MotionBlurEditor() {}
    public static MotionBlurSettings edit(MotionBlurSettings value) {
        float[] strength = {value.strength()}, max = {value.maxPixels()};
        int[] samples = {value.samples()};
        boolean changed = ImGui.sliderFloat(I18n.get("vector3.motion.strength"), strength, 0, 2);
        changed |= ImGui.sliderInt(I18n.get("vector3.motion.samples"), samples, 2, 32);
        changed |= ImGui.sliderFloat(I18n.get("vector3.motion.max_pixels"), max, 1, 128);
        return changed ? new MotionBlurSettings(strength[0], samples[0], max[0]) : value;
    }
}
