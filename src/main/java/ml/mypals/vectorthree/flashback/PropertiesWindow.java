package ml.mypals.vectorthree.flashback;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import net.minecraft.client.resources.language.I18n;


public final class PropertiesWindow {
    public static final String KEYFRAME_POPUP = "##KeyframePopup";
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_properties");
    private static boolean focusRequested;
    // Null until the window has been drawn once, so a docked window isn't closed before its dock is known.
    private static Boolean docked;
    private static long shownKeyframe = Long.MIN_VALUE;

    private PropertiesWindow() {}

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.properties.title"), "", WINDOW.isOpen())) WINDOW.toggle();
    }

    public static void requestFocus() {
        if (!WINDOW.isOpen()) WINDOW.toggle();
        focusRequested = true;
    }

    /**
     * Floating, the window behaves like the popup it replaces: it appears for a newly selected keyframe and
     * closes with the selection. Docked, it stays put and only its contents follow the selection.
     */
    public static boolean begin(boolean hasKeyframe, long keyframe) {
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
        if (visible && hasKeyframe) return true;
        if (visible) ImGui.textDisabled(I18n.get("vector3.properties.empty"));
        end();
        return false;
    }

    public static void end() {
        ImGui.end();
        WINDOW.sync();
    }
}
