package ml.mypals.vectorthree.flashback.fade.effects;

import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

public final class GradingCurvesEditor {
    private static final int[] COLORS = {0xffdddddd, 0xff7070ff, 0xff70ff70, 0xffff7070,
            0xffffa070, 0xff70cfff, 0xffb4ff70, 0xffff70e0};
    private static int selected, dragging = -1;

    private GradingCurvesEditor() {}

    public static GradingCurves edit(GradingCurves curves) {
        if (!ImGui.collapsingHeader(I18n.get("vector3.grade.curves"))) return curves;
        String[] names = GradingCurves.CHANNELS.stream()
                .map(channel -> I18n.get("vector3.grade." + channel)).toArray(String[]::new);
        imgui.moulberry90.type.ImInt choice = new imgui.moulberry90.type.ImInt(selected);
        if (ImGui.combo(I18n.get("vector3.grade.curve_type"), choice, names)) {
            selected = choice.get();
            dragging = -1;
        }
        ColorCurve curve = curves.get(selected);
        float width = Math.max(140, ImGui.getContentRegionAvailX());
        float left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
        float height = 180, pad = 12;
        ImGui.invisibleButton("##grading_curve", width, height);
        boolean hovered = ImGui.isItemHovered();
        float x0 = left + pad, y0 = top + pad, sizeX = width - 2 * pad, sizeY = height - 2 * pad;
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(left, top, left + width, top + height, 0xff1e1e1e);
        for (int i = 0; i <= 4; i++) {
            float x = x0 + sizeX * i / 4f, y = y0 + sizeY * i / 4f;
            draw.addLine(x, y0, x, y0 + sizeY, 0x35ffffff);
            draw.addLine(x0, y, x0 + sizeX, y, 0x35ffffff);
        }
        float previousX = x0, previousY = y0 + sizeY * (1 - curve.evaluate(0));
        for (int i = 1; i <= 128; i++) {
            float x = i / 128f, sx = x0 + x * sizeX, sy = y0 + sizeY * (1 - curve.evaluate(x));
            draw.addLine(previousX, previousY, sx, sy, COLORS[selected], 2);
            previousX = sx; previousY = sy;
        }
        float mx = ImGui.getIO().getMousePosX(), my = ImGui.getIO().getMousePosY();
        int hit = -1;
        for (int i = 0; i < curve.points().size(); i++) {
            ColorCurve.Point point = curve.points().get(i);
            float sx = x0 + point.x() * sizeX, sy = y0 + (1 - point.y()) * sizeY;
            if (Math.hypot(mx - sx, my - sy) < 8) hit = i;
            draw.addCircleFilled(sx, sy, i == dragging ? 6 : 4, i == dragging ? 0xffffffff : COLORS[selected]);
        }
        if (hovered && ImGui.isMouseClicked(0) && hit >= 0) dragging = hit;
        if (!ImGui.isMouseDown(0)) dragging = -1;
        List<ColorCurve.Point> points = new ArrayList<>(curve.points());
        if (dragging >= 0) {
            int i = dragging;
            float x = Math.clamp((mx - x0) / sizeX, 0, 1);
            float y = Math.clamp(1 - (my - y0) / sizeY, 0, 1);
            if (i == 0) x = 0;
            else if (i == points.size() - 1) x = 1;
            else x = Math.clamp(x, points.get(i - 1).x() + 0.004f, points.get(i + 1).x() - 0.004f);
            points.set(i, new ColorCurve.Point(x, y));
        } else if (hovered && ImGui.isMouseDoubleClicked(0) && hit < 0 && points.size() < 16) {
            float x = Math.clamp((mx - x0) / sizeX, 0.01f, 0.99f);
            float y = Math.clamp(1 - (my - y0) / sizeY, 0, 1);
            int i = 1;
            while (i < points.size() && points.get(i).x() < x) i++;
            if (x - points.get(i - 1).x() > 0.01f && points.get(i).x() - x > 0.01f)
                points.add(i, new ColorCurve.Point(x, y));
        } else if (hovered && ImGui.isMouseClicked(1) && hit > 0 && hit < points.size() - 1) {
            points.remove(hit);
        }
        ImGui.textDisabled(I18n.get("vector3.grade.curve_hint"));
        if (ImGui.button(I18n.get("vector3.grade.curve_reset")))
            return curves.with(selected, selected < 4 ? ColorCurve.identity() : ColorCurve.neutral());
        ColorCurve result = new ColorCurve(List.copyOf(points));
        return result.equals(curve) ? curves : curves.with(selected, result);
    }
}
