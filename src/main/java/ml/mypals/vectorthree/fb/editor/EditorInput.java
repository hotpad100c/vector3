package ml.mypals.vectorthree.fb.editor;

import com.mojang.blaze3d.platform.InputConstants;
import com.moulberry.flashback.editor.ui.ReplayUI;
import ml.mypals.vectorthree.mixin.flashback.ReplayUIAccessor;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Raw keyboard / mouse state and Flashback's mouse grab, read from GLFW like Flashback for 26.2 does. ImGui is only
 * fed input while one of its own windows has focus, so viewport tools read the device directly.
 */
public final class EditorInput {
    private EditorInput() {}

    private static long window() {
        return Minecraft.getInstance().getWindow().handle();
    }

    private static boolean either(int left, int right) {
        long window = window();
        return GLFW.glfwGetKey(window, left) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, right) == GLFW.GLFW_PRESS;
    }

    public static boolean isCtrlDown() {
        return either(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean isShiftDown() {
        return either(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isAltDown() {
        return either(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    /** {@code button} counts like ImGui's: 0 left, 1 right, 2 middle. */
    public static boolean isMouseDown(int button) {
        return GLFW.glfwGetMouseButton(window(), button) == GLFW.GLFW_PRESS;
    }

    /** {@code key} is a GLFW key code ({@link InputConstants}' KEY_ constants on 26.2). */
    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public static boolean isGrabbed() {
        return ReplayUI.imguiGlfw.isGrabbed();
    }

    public static void ungrab() {
        ReplayUI.imguiGlfw.ungrab();
    }

    /** Hides the cursor and reports raw motion through the delta getters until {@link #ungrab()}. */
    public static void grab() {
        ReplayUI.imguiGlfw.setGrabbed(false, 0, false, -1, -1);
    }

    public static double grabbedDeltaX() {
        return ReplayUI.imguiGlfw.getGrabbedMouseDeltaX();
    }

    public static double grabbedDeltaY() {
        return ReplayUI.imguiGlfw.getGrabbedMouseDeltaY();
    }

    /** Whether the mouse is over Flashback's game view. */
    public static boolean isMainFrameHovered() {
        return ReplayUIAccessor.vector3$isFrameHovered();
    }
}
