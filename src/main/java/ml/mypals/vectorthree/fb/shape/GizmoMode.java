package ml.mypals.vectorthree.fb.shape;

import com.mojang.blaze3d.platform.InputConstants;
import imgui.moulberry90.ImGui;

import java.util.HashSet;
import java.util.Set;

/** The viewport gizmo mode, shared by every gizmo; G / R / B / M switch it. A gizmo without the mode shows all its handles. */
public enum GizmoMode {
    MOVE, ROTATE, SCALE, GEOMETRY;

    private static GizmoMode current = MOVE;
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
        if (blocked || ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive()) return;
        GizmoMode requested = move ? MOVE : scale ? SCALE : rotate ? ROTATE : geometry ? GEOMETRY : null;
        if (requested != null) current = requested;
    }

    private static boolean pressed(int key) {
        boolean down = InputConstants.isKeyDown(key);
        boolean wasDown = down ? !held.add(key) : held.remove(key);
        return down && !wasDown;
    }
}
