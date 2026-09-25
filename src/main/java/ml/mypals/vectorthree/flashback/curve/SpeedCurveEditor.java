package ml.mypals.vectorthree.flashback.curve;

import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiKey;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The graph editor for a speed curve. Drag points and handles with the left button (Alt breaks a handle
 * from its twin, Ctrl snaps to 0.05), double-click to add a point, right-click a point to remove it.
 */
public final class SpeedCurveEditor {
    public record Result(SpeedCurve curve, SpeedCurve before, boolean commit) {}

    private static final float HEIGHT = 240;
    private static final float PAD = 12;
    private static final float PICK_RADIUS = 7;
    private static final float SNAP = 0.05f;
    private static final int SAMPLES = 128;
    private static final int BACKGROUND = 0xFF1E1E1E, GRID = 0x28FFFFFF, BASELINE = 0x70FFFFFF, CURVE = 0xFF50C8FF;
    private static final int POINT = 0xFFFFFFFF, HANDLE = 0xFFB4B4B4, ACTIVE = 0xFF28EBFF, PLAYHEAD = 0xB04040FF;

    private enum Part { POINT, IN, OUT }

    private static int dragIndex = -1;
    private static Part dragPart;
    private static @Nullable SpeedCurve dragStart;
    private static float[] dragRange;

    private SpeedCurveEditor() {}

    /** Returns null when nothing changed this frame. {@code playhead} is the segment fraction, or negative when outside. */
    public static @Nullable Result render(SpeedCurve curve, float playhead) {
        float width = Math.max(120, ImGui.getContentRegionAvailX());
        float left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##vector3_speed_curve", width, HEIGHT);
        boolean hovered = ImGui.isItemHovered();

        float[] range = dragIndex >= 0 && dragRange != null ? dragRange : range(curve);
        View view = new View(left + PAD, top + PAD, width - PAD * 2, HEIGHT - PAD * 2, range[0], range[1]);
        ImDrawList draw = ImGui.getWindowDrawList();
        drawGrid(draw, view, left, top, width);
        drawCurve(draw, view, curve);

        float mouseX = ImGui.getIO().getMousePosX(), mouseY = ImGui.getIO().getMousePosY();
        Hit hit = dragIndex >= 0 ? new Hit(dragIndex, dragPart) : hovered ? pick(curve, view, mouseX, mouseY) : null;
        drawPoints(draw, view, curve, hit);
        if (playhead >= 0 && playhead <= 1) {
            float x = view.screenX(playhead);
            draw.addLine(x, view.top, x, view.top + view.height, PLAYHEAD, 1.5f);
            draw.addCircleFilled(x, view.screenY(curve.evaluate(playhead)), 4, PLAYHEAD);
        }

        Result result = interact(curve, view, hovered, hit, mouseX, mouseY);
        Result preset = presets(curve);
        ImGui.textDisabled(I18n.get("vector3.curve.hint"));
        return preset != null ? preset : result;
    }

    private static @Nullable Result interact(SpeedCurve curve, View view, boolean hovered, @Nullable Hit hit,
            float mouseX, float mouseY) {
        if (dragIndex >= 0) {
            SpeedCurve start = dragStart;
            if (ImGui.isMouseClicked(1) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                dragIndex = -1;
                return new Result(start, start, false);
            }
            if (!ImGui.isMouseDown(0)) {
                dragIndex = -1;
                return new Result(curve, start, true);
            }
            SpeedCurve moved = dragged(curve, view.curveX(mouseX), view.curveY(mouseY));
            return moved.equals(curve) ? null : new Result(moved, start, false);
        }
        if (!hovered) return null;
        if (ImGui.isMouseClicked(0) && hit != null) {
            dragIndex = hit.index();
            dragPart = hit.part();
            dragStart = curve;
            dragRange = range(curve);
            return null;
        }
        if (ImGui.isMouseClicked(1) && hit != null && hit.part() == Part.POINT
                && hit.index() > 0 && hit.index() < curve.points().size() - 1) {
            List<SpeedCurve.Point> points = new ArrayList<>(curve.points());
            points.remove(hit.index());
            return new Result(new SpeedCurve(points).sanitized(), curve, true);
        }
        if (ImGui.isMouseDoubleClicked(0) && hit == null) {
            return new Result(withPointAt(curve, Math.clamp(view.curveX(mouseX), 0.001f, 0.999f)), curve, true);
        }
        return null;
    }

    private static SpeedCurve dragged(SpeedCurve curve, float x, float y) {
        if (InputHelper.isCtrlDownRaw()) {
            x = Math.round(x / SNAP) * SNAP;
            y = Math.round(y / SNAP) * SNAP;
        }
        List<SpeedCurve.Point> points = new ArrayList<>(curve.points());
        SpeedCurve.Point point = points.get(dragIndex);
        boolean free = InputHelper.isAltDownRaw();
        switch (dragPart) {
            case POINT -> {
                if (dragIndex == 0 || dragIndex == points.size() - 1) return curve;
                float min = points.get(dragIndex - 1).x() + 0.001f, max = points.get(dragIndex + 1).x() - 0.001f;
                points.set(dragIndex, point.withPosition(Math.clamp(x, min, max), y));
            }
            case IN -> {
                float dx = Math.min(0, x - point.x()), dy = y - point.y();
                point = point.withIn(dx, dy);
                if (!free && dragIndex < points.size() - 1) point = point.withOut(mirror(dx, dy, point.outDx(), point.outDy())[0],
                        mirror(dx, dy, point.outDx(), point.outDy())[1]);
                points.set(dragIndex, point);
            }
            case OUT -> {
                float dx = Math.max(0, x - point.x()), dy = y - point.y();
                point = point.withOut(dx, dy);
                if (!free && dragIndex > 0) point = point.withIn(mirror(dx, dy, point.inDx(), point.inDy())[0],
                        mirror(dx, dy, point.inDx(), point.inDy())[1]);
                points.set(dragIndex, point);
            }
        }
        return new SpeedCurve(points).sanitized();
    }

    // The twin handle points the opposite way, keeping its own length.
    private static float[] mirror(float dx, float dy, float twinDx, float twinDy) {
        float length = (float) Math.hypot(dx, dy), twinLength = (float) Math.hypot(twinDx, twinDy);
        if (length < 1.0e-6f) return new float[]{twinDx, twinDy};
        return new float[]{-dx / length * twinLength, -dy / length * twinLength};
    }

    private static SpeedCurve withPointAt(SpeedCurve curve, float x) {
        float y = curve.evaluate(x);
        float e = 0.005f;
        float slope = (curve.evaluate(Math.min(1, x + e)) - curve.evaluate(Math.max(0, x - e))) / (2 * e);
        List<SpeedCurve.Point> points = new ArrayList<>(curve.points());
        int index = 1;
        while (index < points.size() - 1 && points.get(index).x() < x) index++;
        float gap = Math.min(x - points.get(index - 1).x(), points.get(index).x() - x) / 3;
        points.add(index, new SpeedCurve.Point(x, y, -gap, -gap * slope, gap, gap * slope));
        return new SpeedCurve(points).sanitized();
    }

    private static @Nullable Result presets(SpeedCurve curve) {
        Result result = null;
        for (SpeedCurve.Preset preset : SpeedCurve.Preset.values()) {
            if (preset.ordinal() > 0) ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.curve.preset." + preset.name().toLowerCase(java.util.Locale.ROOT)))) {
                result = new Result(SpeedCurve.preset(preset).sanitized(), curve, true);
            }
        }
        return result;
    }

    private record Hit(int index, Part part) {}

    private static @Nullable Hit pick(SpeedCurve curve, View view, float mouseX, float mouseY) {
        List<SpeedCurve.Point> points = curve.points();
        for (int i = 0; i < points.size(); i++) {
            SpeedCurve.Point point = points.get(i);
            if (i > 0 && near(view, point.x() + point.inDx(), point.y() + point.inDy(), mouseX, mouseY)) return new Hit(i, Part.IN);
            if (i < points.size() - 1 && near(view, point.x() + point.outDx(), point.y() + point.outDy(), mouseX, mouseY)) {
                return new Hit(i, Part.OUT);
            }
        }
        for (int i = 0; i < points.size(); i++) {
            if (near(view, points.get(i).x(), points.get(i).y(), mouseX, mouseY)) return new Hit(i, Part.POINT);
        }
        return null;
    }

    private static boolean near(View view, float x, float y, float mouseX, float mouseY) {
        return Math.hypot(view.screenX(x) - mouseX, view.screenY(y) - mouseY) <= PICK_RADIUS;
    }

    private static float[] range(SpeedCurve curve) {
        float min = -0.25f, max = 1.25f;
        for (SpeedCurve.Point point : curve.points()) {
            min = Math.min(min, Math.min(point.y(), Math.min(point.y() + point.inDy(), point.y() + point.outDy())));
            max = Math.max(max, Math.max(point.y(), Math.max(point.y() + point.inDy(), point.y() + point.outDy())));
        }
        return new float[]{min, max};
    }

    private static void drawGrid(ImDrawList draw, View view, float left, float top, float width) {
        draw.addRectFilled(left, top, left + width, top + HEIGHT, BACKGROUND);
        for (int i = 0; i <= 10; i++) {
            float x = view.screenX(i / 10f);
            draw.addLine(x, view.top, x, view.top + view.height, GRID);
        }
        for (float y = (float) Math.ceil(view.minY * 4) / 4; y <= view.maxY; y += 0.25f) {
            boolean bold = Math.abs(y) < 1.0e-4f || Math.abs(y - 1) < 1.0e-4f;
            float screenY = view.screenY(y);
            draw.addLine(view.left, screenY, view.left + view.width, screenY, bold ? BASELINE : GRID, bold ? 1.5f : 1);
        }
    }

    private static void drawCurve(ImDrawList draw, View view, SpeedCurve curve) {
        float previousX = view.screenX(0), previousY = view.screenY(curve.evaluate(0));
        for (int i = 1; i <= SAMPLES; i++) {
            float t = i / (float) SAMPLES;
            float x = view.screenX(t), y = view.screenY(curve.evaluate(t));
            draw.addLine(previousX, previousY, x, y, CURVE, 2);
            previousX = x;
            previousY = y;
        }
    }

    private static void drawPoints(ImDrawList draw, View view, SpeedCurve curve, @Nullable Hit hit) {
        List<SpeedCurve.Point> points = curve.points();
        for (int i = 0; i < points.size(); i++) {
            SpeedCurve.Point point = points.get(i);
            float px = view.screenX(point.x()), py = view.screenY(point.y());
            if (i > 0) drawHandle(draw, px, py, view.screenX(point.x() + point.inDx()), view.screenY(point.y() + point.inDy()),
                    hit != null && hit.index() == i && hit.part() == Part.IN);
            if (i < points.size() - 1) drawHandle(draw, px, py, view.screenX(point.x() + point.outDx()),
                    view.screenY(point.y() + point.outDy()), hit != null && hit.index() == i && hit.part() == Part.OUT);
            int colour = hit != null && hit.index() == i && hit.part() == Part.POINT ? ACTIVE : POINT;
            draw.addRectFilled(px - 4, py - 4, px + 4, py + 4, colour);
        }
    }

    private static void drawHandle(ImDrawList draw, float px, float py, float hx, float hy, boolean active) {
        draw.addLine(px, py, hx, hy, HANDLE);
        draw.addCircleFilled(hx, hy, 3.5f, active ? ACTIVE : HANDLE);
    }

    private record View(float left, float top, float width, float height, float minY, float maxY) {
        float screenX(float x) { return left + x * width; }
        float screenY(float y) { return top + (maxY - y) / (maxY - minY) * height; }
        float curveX(float screenX) { return (screenX - left) / width; }
        float curveY(float screenY) { return maxY - (screenY - top) / height * (maxY - minY); }
    }
}
