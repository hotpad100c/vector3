package ml.mypals.vectorthree.expression;

import com.mojang.logging.LogUtils;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiIO;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.internal.ImGuiContext;
import org.slf4j.Logger;

/**
 * A private ImGui context for running keyframe editors when no UI frame is open (keyframes are applied before the
 * UI draws, and exports draw no UI). It is never rendered; its own default font keeps it apart from Flashback's atlas.
 */
public final class HeadlessImGui {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_FAILURES = 5;
    private static final int FLAGS = ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoInputs
            | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoDecoration;

    private static ImGuiContext context;
    private static int depth, failures;

    private HeadlessImGui() {}

    /** Runs {@code body} inside a frame of the private context; false if it could not. */
    public static boolean run(Runnable body) {
        if (depth > 0) {
            body.run();
            return true;
        }
        if (failures >= MAX_FAILURES) return false;
        ImGuiContext previous = ImGui.getCurrentContext();
        try {
            if (context == null) create();
            ImGui.setCurrentContext(context);
            ImGui.getIO().setDeltaTime(1 / 60f);
            ImGui.newFrame();
            ImGui.setNextWindowPos(0, 0);
            ImGui.begin("##vector3_headless", FLAGS);
            depth++;
            try {
                body.run();
            } finally {
                depth--;
            }
            ImGui.end();
            ImGui.endFrame();
            return true;
        } catch (RuntimeException | Error error) {
            // Ending a frame an editor left unbalanced would fail an ImGui assertion (which exits the game), so the
            // context is dropped mid-frame instead and a fresh one made next time.
            failures++;
            LOGGER.warn("Expression evaluation failed inside the headless ImGui context", error);
            discard();
            return false;
        } finally {
            ImGui.setCurrentContext(previous);
            // getIO(), getStyle() and getMainViewport() hand out shared wrappers; point them back at the caller's context.
            if (previous.isValidPtr()) {
                ImGui.getIO().getFonts();
                ImGui.getStyle();
                ImGui.getMainViewport();
            }
        }
    }

    private static void create() {
        context = ImGui.createContext();
        ImGui.setCurrentContext(context);
        ImGuiIO io = ImGui.getIO();
        // Never setIniFilename: the binding frees the previous name, which corrupts the native heap. With this rate the
        // default file is only written if a failed context is dropped.
        io.setIniSavingRate(Float.MAX_VALUE);
        io.setDisplaySize(4096, 4096);
        io.getFonts().addFontDefault();
        io.getFonts().build();
    }

    private static void discard() {
        if (context == null) return;
        try {
            ImGui.destroyContext(context);
        } catch (RuntimeException | Error ignored) {
        }
        context = null;
    }
}
