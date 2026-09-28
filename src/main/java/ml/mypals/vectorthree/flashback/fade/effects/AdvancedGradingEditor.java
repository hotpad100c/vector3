package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.fade.effects.ColorGradingSettings.Rgb;
import net.minecraft.client.resources.language.I18n;

public final class AdvancedGradingEditor {
    private AdvancedGradingEditor() {}

    public static AdvancedGradingSettings edit(AdvancedGradingSettings value) {
        Rgb splitShadows = value.splitShadows(), splitHighlights = value.splitHighlights();
        Rgb shadows = value.shadows(), midtones = value.midtones(), highlights = value.highlights();
        float[] balance = {value.splitBalance()}, shadowEnd = {value.shadowEnd()};
        float[] highlightStart = {value.highlightStart()};
        boolean changed = false;
        if (ImGui.collapsingHeader(I18n.get("vector3.grade.split_toning"))) {
            splitShadows = ColorWheelEditor.edit(I18n.get("vector3.grade.split_shadows"), splitShadows, -1, 1);
            splitHighlights = ColorWheelEditor.edit(I18n.get("vector3.grade.split_highlights"), splitHighlights, -1, 1);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.split_balance"), balance, -1, 1);
        }
        if (ImGui.collapsingHeader(I18n.get("vector3.grade.smh"))) {
            shadows = ColorWheelEditor.edit(I18n.get("vector3.grade.smh_shadows"), shadows, 0, 3);
            midtones = ColorWheelEditor.edit(I18n.get("vector3.grade.smh_midtones"), midtones, 0, 3);
            highlights = ColorWheelEditor.edit(I18n.get("vector3.grade.smh_highlights"), highlights, 0, 3);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.smh_shadow_end"), shadowEnd, 0.01f, 0.49f);
            changed |= ImGui.sliderFloat(I18n.get("vector3.grade.smh_highlight_start"), highlightStart, 0.51f, 0.99f);
        }
        return changed || !splitShadows.equals(value.splitShadows()) || !splitHighlights.equals(value.splitHighlights())
                || !shadows.equals(value.shadows()) || !midtones.equals(value.midtones())
                || !highlights.equals(value.highlights())
                ? new AdvancedGradingSettings(splitShadows, splitHighlights, balance[0], shadows, midtones,
                highlights, shadowEnd[0], highlightStart[0]) : value;
    }
}
