package ml.mypals.vectorthree.camera;

import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shapeManagers.EmptyShapeManager;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManager;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** vector3's editor-only shapes (gizmos, selection boxes, placement previews), switched off for a render pass. */
final class EditorOverlays {
    private static final List<String> PREFIXES = List.of("gizmo", "camera_gizmo", "orbit_gizmo", "prefab_gizmo",
            "prefab_preview", "eyedropper_highlight", "pose_gizmo");
    private static final List<Shape> hidden = new ArrayList<>();

    private EditorOverlays() {}

    static void hide() {
        for (ShapeManager manager : ShapeManagers.managers) {
            for (ShapeManager.ShapeGroup group : List.of(manager.immediateShapeGroup, manager.batchShapeGroup, manager.bufferedShapeGroup)) {
                hide(group.normalShapeMap);
                hide(group.seeThroughShapeMap);
            }
        }
        for (EmptyShapeManager manager : ShapeManagers.emptyManagers) hide(manager.shapeGroup.shapeMap);
    }

    static void restore() {
        for (Shape shape : hidden) shape.enabled = true;
        hidden.clear();
    }

    private static void hide(Map<Identifier, Shape> shapes) {
        for (Map.Entry<Identifier, Shape> entry : shapes.entrySet()) {
            if (!entry.getValue().enabled || !isOverlay(entry.getKey())) continue;
            entry.getValue().enabled = false;
            hidden.add(entry.getValue());
        }
    }

    private static boolean isOverlay(Identifier id) {
        if (!id.getNamespace().equals(Vector3.MOD_ID)) return false;
        String path = id.getPath();
        for (String prefix : PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "_")) return true;
        }
        return false;
    }
}
