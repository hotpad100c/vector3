package ml.mypals.vectorthree.multiedit;

import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import net.minecraft.client.resources.language.I18n;

/** Covers the disagreeing parts of the widget just drawn with a "-". */
final class MixedOverlay {
    private static final String DASH = "-";

    private MixedOverlay() {}

    static void draw(String label, boolean[] mixed, MultiEditSession.Kind kind, boolean locked) {
        if (locked && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(I18n.get("vector3.multi.locked"));
        }
        if (kind == MultiEditSession.Kind.BUTTON || ImGui.isItemActive()) return;
        float minX = ImGui.getItemRectMinX(), minY = ImGui.getItemRectMinY();
        float maxX = ImGui.getItemRectMaxX(), maxY = ImGui.getItemRectMaxY();
        float frameHeight = ImGui.getFrameHeight();
        maxY = Math.min(maxY, minY + frameHeight);
        if (kind == MultiEditSession.Kind.CHECK || kind == MultiEditSession.Kind.RADIO) {
            cover(minX, minY, minX + frameHeight, maxY);
            return;
        }
        float spacing = ImGui.getStyle().getItemInnerSpacingX();
        String visible = visibleLabel(label);
        float frameMaxX = visible.isEmpty() ? maxX : maxX - ImGui.calcTextSizeX(visible) - spacing;
        int parts = mixed.length;
        float width = (frameMaxX - minX - spacing * (parts - 1)) / parts;
        for (int i = 0; i < parts; i++) {
            if (!mixed[i]) continue;
            float x = minX + i * (width + spacing);
            cover(x, minY, x + width, maxY);
        }
    }

    private static void cover(float x0, float y0, float x1, float y1) {
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = ImGui.getStyle().getFrameRounding();
        draw.addRectFilled(x0, y0, x1, y1, ImGui.getColorU32(ImGuiCol.WindowBg), rounding);
        draw.addRectFilled(x0, y0, x1, y1, ImGui.getColorU32(ImGuiCol.FrameBg), rounding);
        float textX = (x0 + x1 - ImGui.calcTextSizeX(DASH)) / 2, textY = (y0 + y1 - ImGui.calcTextSizeY(DASH)) / 2;
        draw.addText(textX, textY, ImGui.getColorU32(ImGuiCol.TextDisabled), DASH);
    }

    private static String visibleLabel(String label) {
        int hidden = label.indexOf("##");
        return hidden >= 0 ? label.substring(0, hidden) : label;
    }
}
