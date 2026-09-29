package ml.mypals.vectorthree.fb.editor;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import ml.mypals.vectorthree.fb.multiedit.PropertySelection;
import net.minecraft.client.resources.language.I18n;


public final class PropertiesWindow {
    public static final String KEYFRAME_POPUP = "##KeyframePopup";
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_properties");
    private static boolean focusRequested;
    private static Boolean docked;
    private static long shownKeyframe = Long.MIN_VALUE;
    private static boolean curveTab;
    private static float windowX, windowY, windowWidth, windowHeight;

    private PropertiesWindow() {}

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.properties.title"), "", WINDOW.isOpen())) WINDOW.toggle();
    }

    public static void requestFocus() {
        if (!WINDOW.isOpen()) WINDOW.toggle();
        focusRequested = true;
    }


    public static boolean isCurveTab() {
        return curveTab;
    }

    public static boolean begin(boolean hasKeyframe, long keyframe, boolean hasCurve) {
        curveTab = false;
        if (docked != null && !docked) {
            if (hasKeyframe && keyframe != shownKeyframe && !WINDOW.isOpen()) WINDOW.toggle();
            if (!hasKeyframe && WINDOW.isOpen()) WINDOW.toggle();
        }
        shownKeyframe = hasKeyframe ? keyframe : Long.MIN_VALUE;
        if (!WINDOW.isOpen()) return false;
        if (focusRequested) {
            ImGui.setNextWindowFocus();
            focusRequested = false;
        }
        ImGui.setNextWindowSize(380, 480, ImGuiCond.FirstUseEver);
        // Dragging on the body selects rows, so it must not also drag a floating window.
        boolean inBody = ImGui.getIO().getMousePosX() >= windowX && ImGui.getIO().getMousePosX() <= windowX + windowWidth
                && ImGui.getIO().getMousePosY() >= windowY + ImGui.getFrameHeight() && ImGui.getIO().getMousePosY() <= windowY + windowHeight;
        int flags = Boolean.FALSE.equals(docked) && (PropertySelection.pressing() || inBody && ImGui.isMouseClicked(0))
                ? ImGuiWindowFlags.NoMove : 0;
        boolean visible = ImGui.begin(WINDOW.title(I18n.get("vector3.properties.title")), WINDOW.open(), flags);
        windowX = ImGui.getWindowPosX();
        windowY = ImGui.getWindowPosY();
        windowWidth = ImGui.getWindowWidth();
        windowHeight = ImGui.getWindowHeight();
        docked = ImGui.isWindowDocked();
        if (visible && hasKeyframe) {
            if (hasCurve && ImGui.beginTabBar("##vector3_properties_tabs")) {
                if (ImGui.beginTabItem(I18n.get("vector3.properties.tab.keyframe"))) ImGui.endTabItem();
                if (ImGui.beginTabItem(I18n.get("vector3.curve.tab"))) {
                    curveTab = true;
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
            return true;
        }
        if (visible) ImGui.textDisabled(I18n.get("vector3.properties.empty"));
        end();
        return false;
    }

    public static void end() {
        ImGui.end();
        WINDOW.sync();
    }
}
