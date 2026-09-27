package ml.mypals.vectorthree.flashback;

import imgui.moulberry90.flag.ImGuiKey;
import ml.mypals.vectorthree.mixin.flashback.KeybindAccessor;
import ml.mypals.vectorthree.mixin.flashback.KeybindFactory;

import java.util.List;

public final class VectorKeybinds {
    public static final Object MARK_IN = bind("vector3.mark_in", ImGuiKey.I, false, false, true, false);
    public static final Object MARK_OUT = bind("vector3.mark_out", ImGuiKey.O, false, false, true, false);
    public static final Object CLEAR_IN = bind("vector3.clear_in", ImGuiKey.I, true, false, true, false);
    public static final Object CLEAR_OUT = bind("vector3.clear_out", ImGuiKey.O, true, false, true, false);
    public static final Object HELP = bind("vector3.help", ImGuiKey.Slash, true, false, false, false);
    private static final List<Object> ALL = List.of(MARK_IN, MARK_OUT, CLEAR_IN, CLEAR_OUT, HELP);

    private VectorKeybinds() {}
    public static List<Object> all() { return ALL; }
    public static boolean pressed(Object keybind) { return ((KeybindAccessor) keybind).vector3$isPressed(false); }

    private static Object bind(String description, int key) { return bind(description, key, false, false, false, false); }
    private static Object bind(String description, int key, boolean shift, boolean ctrl, boolean alt, boolean superMod) {
        return KeybindFactory.vector3$create(description, key, shift, ctrl, alt, superMod);
    }
}
