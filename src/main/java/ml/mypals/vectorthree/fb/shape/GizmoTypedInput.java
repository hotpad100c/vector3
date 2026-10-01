package ml.mypals.vectorthree.fb.shape;

import ml.mypals.vectorthree.fb.editor.EditorInput;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiKey;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/**
 * Typed values for any viewport gizmo, as in Blender: G / R / B, then an axis and a number (G X 1.5 moves 1.5 along
 * X, R Z 90 turns 90 degrees, B 2 scales by 2). Enter or a click applies it, Esc or a right click cancels, and
 * Backspace takes the last key back (an axis returns to the one before it). The gizmo only says what a typed value
 * does to what it edits, through {@link Target}.
 */
public final class GizmoTypedInput {
    public enum Axis { NONE, X, Y, Z }

    public interface Target {
        /** What is being edited; the input is cancelled when this changes. */
        Object identity();

        /** A gesture starts: remember the starting state. */
        void begin();

        /** Show {@code value} along {@code axis} (NONE for none) for {@code mode} (MOVE, ROTATE or SCALE), from the starting state. */
        void preview(GizmoMode mode, Axis axis, double value);

        /** Back to the starting state. */
        void restore();

        /** Keep the last preview. */
        void commit();
    }

    private static final java.util.Set<Integer> HELD = new java.util.HashSet<>();

    // Read from GLFW directly, like the G / R / B keys: ImGui is only fed the keyboard while one of its own windows
    // has focus, so it misses keys pressed right after clicking the viewport.
    private static boolean pressed(int key) {
        boolean down = EditorInput.isKeyDown(key);
        boolean wasDown = down ? !HELD.add(key) : HELD.remove(key);
        return down && !wasDown;
    }

    private GizmoMode mode;
    private Axis axis = Axis.NONE;
    private final StringBuilder text = new StringBuilder();
    // What each key did, newest first, so Backspace can take them back one at a time.
    private final ArrayDeque<Object> history = new ArrayDeque<>();
    private Target target;
    private Object identity;
    private boolean applied;

    public boolean active() {
        return mode != null;
    }

    /**
     * Once per frame. {@code target} is null when the gizmo has nothing selected or is being dragged, which
     * cancels a running input.
     */
    public void frame(boolean inViewport, @Nullable Target target) {
        if (target == null) {
            end(false);
            return;
        }
        if (mode != null && !target.identity().equals(identity)) end(false);
        GizmoMode pressed = GizmoMode.pressedThisFrame();
        boolean modifier = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyAlt();
        if (pressed != null && pressed != GizmoMode.GEOMETRY && !modifier) {
            end(false);
            mode = pressed;
            this.target = target;
            identity = target.identity();
            target.begin();
            // Keys held while the mode key went down must not count as typed.
            drain();
        }
        boolean typing = mode != null && inViewport && !ImGui.getIO().getWantTextInput();
        // Always sampled, so a key pressed while typing is blocked is not seen as new when it is unblocked.
        if (!typing) {
            drain();
            return;
        }
        boolean changed = keys();
        boolean escape = pressed(GLFW.GLFW_KEY_ESCAPE);
        boolean enter = pressed(GLFW.GLFW_KEY_ENTER) | pressed(GLFW.GLFW_KEY_KP_ENTER);
        if (escape || ImGui.isMouseClicked(1)) {
            end(false);
            return;
        }
        if (enter || ImGui.isMouseClicked(0)) {
            end(true);
            return;
        }
        if (changed) preview();
        draw();
    }

    private boolean keys() {
        boolean changed = false;
        Axis picked = pressed(GLFW.GLFW_KEY_X) ? Axis.X : pressed(GLFW.GLFW_KEY_Y) ? Axis.Y
                : pressed(GLFW.GLFW_KEY_Z) ? Axis.Z : null;
        if (picked != null && picked != axis) {
            history.push(axis);
            axis = picked;
            changed = true;
        }
        for (int digit = 0; digit < 10; digit++) {
            boolean row = pressed(GLFW.GLFW_KEY_0 + digit), pad = pressed(GLFW.GLFW_KEY_KP_0 + digit);
            if (row || pad) {
                text.append(digit);
                history.push('c');
                changed = true;
            }
        }
        boolean point = pressed(GLFW.GLFW_KEY_PERIOD) | pressed(GLFW.GLFW_KEY_KP_DECIMAL);
        if (point && text.indexOf(".") < 0) {
            boolean lead = text.isEmpty() || text.toString().equals("-");
            text.append(lead ? "0." : ".");
            history.push(lead ? "0." : ".");
            changed = true;
        }
        if (pressed(GLFW.GLFW_KEY_MINUS) | pressed(GLFW.GLFW_KEY_KP_SUBTRACT)) {
            toggleMinus();
            history.push('-');
            changed = true;
        }
        if (pressed(GLFW.GLFW_KEY_BACKSPACE) && !history.isEmpty()) {
            Object last = history.pop();
            if (last instanceof Axis before) axis = before;
            else if (last.equals('-')) toggleMinus();
            else if (last.equals("0.")) text.setLength(Math.max(0, text.length() - 2));
            else text.setLength(Math.max(0, text.length() - 1));
            changed = true;
        }
        return changed;
    }

    // Reads every key the input uses, so edges seen while typing is blocked are not replayed later.
    private static void drain() {
        int[] keys = {GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_KP_DECIMAL,
                GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_ESCAPE,
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER};
        for (int key : keys) pressed(key);
        for (int digit = 0; digit < 10; digit++) {
            pressed(GLFW.GLFW_KEY_0 + digit);
            pressed(GLFW.GLFW_KEY_KP_0 + digit);
        }
    }

    private void toggleMinus() {
        if (!text.isEmpty() && text.charAt(0) == '-') text.deleteCharAt(0);
        else text.insert(0, '-');
    }

    private @Nullable Double number() {
        String value = text.toString();
        if (value.isEmpty() || value.equals("-") || value.equals("-.")) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void preview() {
        Double value = number();
        if (value == null) {
            if (applied) target.restore();
            applied = false;
            return;
        }
        applied = true;
        target.preview(mode, axis, value);
    }

    private void end(boolean apply) {
        if (mode == null) return;
        if (applied) {
            if (apply) target.commit();
            else target.restore();
        }
        mode = null;
        axis = Axis.NONE;
        text.setLength(0);
        history.clear();
        applied = false;
        target = null;
        identity = null;
    }

    private void draw() {
        String label = I18n.get(mode == GizmoMode.MOVE ? "vector3.gizmo.move"
                : mode == GizmoMode.ROTATE ? "vector3.gizmo.rotate" : "vector3.gizmo.scale");
        String shown = label + (axis == Axis.NONE ? "" : " " + axis) + (text.isEmpty() ? "" : " " + text);
        float x = ImGui.getIO().getMousePosX() + 18, y = ImGui.getIO().getMousePosY() + 18;
        var draw = ImGui.getForegroundDrawList();
        draw.addRectFilled(x - 4, y - 2, x + ImGui.calcTextSizeX(shown) + 4, y + ImGui.getTextLineHeight() + 2, 0xB0000000, 3);
        draw.addText(x, y, 0xFFFFFFFF, shown);
    }
}
