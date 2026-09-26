package ml.mypals.vectorthree.multiedit;

import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.flag.ImGuiFocusedFlags;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import imgui.moulberry90.flag.ImGuiKey;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PropertySelection {
    private record Row(String id, String key, MultiEditSession.Kind kind, Object value,
            float x0, float y0, float x1, float y1) {
        boolean contains(float x, float y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }

        boolean overlaps(float ax, float ay, float bx, float by) {
            return x0 <= Math.max(ax, bx) && x1 >= Math.min(ax, bx) && y0 <= Math.max(ay, by) && y1 >= Math.min(ay, by);
        }
    }

    private static final String MENU = "##vector3_property_clipboard";
    private static final float DRAG_THRESHOLD = 4;
    private static final long MESSAGE_NANOS = 2_500_000_000L;

    private static final List<Row> ROWS = new ArrayList<>();
    private static final Set<String> SELECTED = new LinkedHashSet<>();
    private static Object context;
    private static boolean pressing, boxing;
    private static float startX, startY;
    private static String message;
    private static long messageUntil;

    private PropertySelection() {}

    public static void beginFrame(Object selectionContext) {
        ROWS.clear();
        if (!Objects.equals(selectionContext, context)) {
            context = selectionContext;
            SELECTED.clear();
        }
    }

    static void record(String scope, String key, MultiEditSession.Kind kind, Object value) {
        // A keyframe's tick is where it sits, not something to carry over to another keyframe.
        if (key.startsWith(I18n.get("flashback.tick") + "#")) return;
        Row row = new Row(scope + "|" + key, key, kind, value,
                ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY());
        ROWS.add(row);
        if (SELECTED.contains(row.id())) {
            ImGui.getWindowDrawList().addRectFilled(row.x0() - 2, row.y0() - 1, row.x1() + 2, row.y1() + 1,
                    ImGui.getColorU32(ImGuiCol.Header, 0.45f), 3);
        }
    }

    public static @Nullable List<PropertyClipboard.Clip> frame() {
        float mouseX = ImGui.getIO().getMousePosX(), mouseY = ImGui.getIO().getMousePosY();
        boolean hoveredWindow = ImGui.isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows);
        boolean ctrl = InputHelper.isCtrlDownRaw();
        List<PropertyClipboard.Clip> paste = null;

        if (ctrl && hoveredWindow && ImGui.isMouseClicked(1)) {
            pressing = true;
            boxing = false;
            startX = mouseX;
            startY = mouseY;
        }
        if (pressing && ImGui.isMouseDown(1)) {
            if (Math.hypot(mouseX - startX, mouseY - startY) > DRAG_THRESHOLD) boxing = true;
            if (boxing) {
                ImDrawList draw = ImGui.getWindowDrawList();
                draw.addRectFilled(startX, startY, mouseX, mouseY, ImGui.getColorU32(ImGuiCol.Header, 0.2f));
                draw.addRect(startX, startY, mouseX, mouseY, ImGui.getColorU32(ImGuiCol.Header));
            }
        } else if (pressing) {
            pressing = false;
            if (boxing) {
                for (Row row : ROWS) {
                    if (row.overlaps(startX, startY, mouseX, mouseY)) SELECTED.add(row.id());
                }
            } else {
                Row row = rowAt(mouseX, mouseY);
                if (row != null && !SELECTED.remove(row.id())) SELECTED.add(row.id());
            }
            boxing = false;
        } else if (!ctrl && hoveredWindow && ImGui.isMouseReleased(1)) {
            Row row = rowAt(mouseX, mouseY);
            if (row == null || SELECTED.contains(row.id())) ImGui.openPopup(MENU);
        }

        boolean focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) && !ImGui.getIO().getWantTextInput();
        if (focused && ImGui.isKeyPressed(ImGuiKey.Escape)) SELECTED.clear();
        if (focused && ctrl && ImGui.isKeyPressed(ImGuiKey.C, false)) copy();
        if (focused && ctrl && ImGui.isKeyPressed(ImGuiKey.V, false)) paste = PropertyClipboard.get();

        if (ImGui.beginPopup(MENU)) {
            if (ImGui.menuItem(I18n.get("vector3.properties.copy", selectedRows().size()), "Ctrl+C", false,
                    !SELECTED.isEmpty())) copy();
            if (ImGui.menuItem(I18n.get("vector3.properties.paste", PropertyClipboard.get().size()), "Ctrl+V", false,
                    !PropertyClipboard.get().isEmpty())) paste = PropertyClipboard.get();
            if (ImGui.menuItem(I18n.get("vector3.properties.clear_selection"), "Esc", false, !SELECTED.isEmpty())) {
                SELECTED.clear();
            }
            ImGui.endPopup();
        }

        if (message != null && System.nanoTime() < messageUntil) {
            ImGui.textDisabled(message);
        } else if (!SELECTED.isEmpty() || !PropertyClipboard.get().isEmpty()) {
            ImGui.textDisabled(I18n.get("vector3.properties.clipboard_hint", SELECTED.size(), PropertyClipboard.get().size()));
        }
        return paste == null || paste.isEmpty() ? null : paste;
    }

    public static void pasted(int applied, int total) {
        show(I18n.get("vector3.properties.pasted", applied, total));
    }

    private static void copy() {
        List<PropertyClipboard.Clip> rows = selectedRows();
        if (rows.isEmpty()) return;
        PropertyClipboard.set(rows);
        show(I18n.get("vector3.properties.copied", rows.size()));
    }

    private static List<PropertyClipboard.Clip> selectedRows() {
        Map<String, PropertyClipboard.Clip> clips = new LinkedHashMap<>();
        for (Row row : ROWS) {
            if (SELECTED.contains(row.id())) clips.putIfAbsent(row.key(), new PropertyClipboard.Clip(row.key(), row.kind(), row.value()));
        }
        return new ArrayList<>(clips.values());
    }

    private static @Nullable Row rowAt(float x, float y) {
        for (Row row : ROWS) {
            if (row.contains(x, y)) return row;
        }
        return null;
    }

    private static void show(String text) {
        message = text;
        messageUntil = System.nanoTime() + MESSAGE_NANOS;
    }
}
