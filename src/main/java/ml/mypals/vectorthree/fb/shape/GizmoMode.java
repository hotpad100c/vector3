package ml.mypals.vectorthree.fb.shape;

import ml.mypals.vectorthree.fb.editor.EditorInput;
import com.mojang.blaze3d.platform.InputConstants;
import imgui.moulberry90.ImGui;

import java.util.HashSet;
import java.util.Set;

/** The viewport gizmo mode, shared by every gizmo; G / R / B / M switch it. A gizmo without the mode shows all its handles. */
public enum GizmoMode {
    MOVE, ROTATE, SCALE, GEOMETRY;

    private static GizmoMode current = MOVE;
    private static GizmoMode pressedNow;
    private static final Set<Integer> held = new HashSet<>();

    public static GizmoMode current() {
        return current;
    }

    public static void set(GizmoMode mode) {
        current = mode;
    }

    /** Once per frame; {@code blocked} while a gizmo is dragged. */
    public static void pollShortcuts(boolean blocked) {
        boolean move = pressed(InputConstants.KEY_G);
        boolean scale = pressed(InputConstants.KEY_B);
        boolean rotate = pressed(InputConstants.KEY_R);
        boolean geometry = pressed(InputConstants.KEY_M);
        pressedNow = null;
        if (blocked || ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive()) return;
        GizmoMode requested = move ? MOVE : scale ? SCALE : rotate ? ROTATE : geometry ? GEOMETRY : null;
        pressedNow = requested;
        if (requested != null) current = requested;
    }

    /** The mode key pressed this frame (G, B or R starts a typed value like G X 5), or null. */
    public static GizmoMode pressedThisFrame() {
        return pressedNow;
    }

    private static boolean pressed(int key) {
        boolean down = EditorInput.isKeyDown(key);
        boolean wasDown = down ? !held.add(key) : held.remove(key);
        return down && !wasDown;
    }
}
