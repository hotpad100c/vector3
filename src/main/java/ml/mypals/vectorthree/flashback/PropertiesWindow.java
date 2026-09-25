package ml.mypals.vectorthree.flashback;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import net.minecraft.client.resources.language.I18n;


public final class PropertiesWindow {
    public static final String KEYFRAME_POPUP = "##KeyframePopup";
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_properties");
    private static boolean focusRequested;
    private static Boolean docked;
    private static long shownKeyframe = Long.MIN_VALUE;
    private static boolean curveTab;

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
        boolean visible = ImGui.begin(WINDOW.title(I18n.get("vector3.properties.title")), WINDOW.open());
        docked = ImGui.isWindowDocked();
        if (visible && hasKeyframe) {
            // The speed curve page only exists for keyframes set to the custom interpolation.
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
