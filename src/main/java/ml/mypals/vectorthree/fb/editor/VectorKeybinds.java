package ml.mypals.vectorthree.fb.editor;

import ml.mypals.vectorthree.mixin.flashback.KeybindAccessor;
import ml.mypals.vectorthree.mixin.flashback.KeybindFactory;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Flashback for 26.2 stores keybinds as GLFW key codes. */
public final class VectorKeybinds {
    public static final Object HELP = bind("vector3.help", GLFW.GLFW_KEY_SLASH, true, false, false, false);
    private static final List<Object> ALL = List.of(HELP);

    private VectorKeybinds() {}
    public static List<Object> all() { return ALL; }
    public static boolean pressed(Object keybind) { return ((KeybindAccessor) keybind).vector3$isPressed(false); }

    private static Object bind(String description, int key, boolean shift, boolean ctrl, boolean alt, boolean superMod) {
        return KeybindFactory.vector3$create(description, key, shift, ctrl, alt, superMod);
    }
}
