package ml.mypals.vectorthree.mc.camera;

import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.core.port.Ports;
import ml.mypals.vectorthree.mc.render.ShapeHider;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import net.minecraft.resources.Identifier;

import java.util.List;

/** vector3's editor-only shapes (gizmos, motion paths, selection boxes, placement previews), switched off for a render pass. */
public final class EditorOverlays {
    private static final List<String> PREFIXES = List.of("gizmo", "camera_gizmo", "orbit_gizmo", "prefab_gizmo",
            "prefab_preview", "eyedropper_highlight", "pose_gizmo", "focus_plane_gizmo", "light_gizmo", "motion_path");
    private static final ShapeHider HIDER = new ShapeHider();
    private static final ShapeHider EXPORT_HIDER = new ShapeHider();

    private EditorOverlays() {}

    static void hide() {
        HIDER.hide(EditorOverlays::isOverlay);
    }

    static void restore() {
        HIDER.restore();
    }

    /** Around each rendered frame of an export, so none of the editor's overlays end up in the video. */
    public static void hideForExport() {
        if (Ports.clock().exporting()) EXPORT_HIDER.hide(EditorOverlays::isOverlay);
    }

    public static void restoreAfterExport() {
        EXPORT_HIDER.restore();
    }

    private static boolean isOverlay(Identifier id) {
        if ("particle".equals(ShapeTrackRegistry.typeOf(id.toString()))) return true;
        if (!id.getNamespace().equals(Mod.ID)) return false;
        String path = id.getPath();
        for (String prefix : PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "_")) return true;
        }
        return false;
    }
}
