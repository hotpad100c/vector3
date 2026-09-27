package ml.mypals.vectorthree.camera;

import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.render.ShapeHider;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.resources.Identifier;

import java.util.List;

/** vector3's editor-only shapes (gizmos, selection boxes, placement previews), switched off for a render pass. */
final class EditorOverlays {
    private static final List<String> PREFIXES = List.of("gizmo", "camera_gizmo", "orbit_gizmo", "prefab_gizmo",
            "prefab_preview", "eyedropper_highlight", "pose_gizmo");
    private static final ShapeHider HIDER = new ShapeHider();

    private EditorOverlays() {}

    static void hide() {
        HIDER.hide(EditorOverlays::isOverlay);
    }

    static void restore() {
        HIDER.restore();
    }

    private static boolean isOverlay(Identifier id) {
        if ("particle".equals(ShapeTrackRegistry.typeOf(id.toString()))) return true;
        if (!id.getNamespace().equals(Vector3.MOD_ID)) return false;
        String path = id.getPath();
        for (String prefix : PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "_")) return true;
        }
        return false;
    }
}
