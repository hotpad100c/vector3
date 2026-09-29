package ml.mypals.vectorthree.fb;

import ml.mypals.vectorthree.fb.camera.CameraGizmoEditor;
import ml.mypals.vectorthree.fb.camera.EditorCameraController;
import ml.mypals.vectorthree.fb.camera.orbit.OrbitGizmoEditor;
import ml.mypals.vectorthree.fb.fade.FocusPlaneGizmo;
import ml.mypals.vectorthree.fb.pose.PoseGizmoEditor;
import ml.mypals.vectorthree.fb.prefab.PrefabPlacement;
import ml.mypals.vectorthree.fb.shape.ShapeGizmoEditor;

/** The editor-side singletons: viewport gizmos and the editor camera. */
public final class Editors {
    public static final ShapeGizmoEditor GIZMO_EDITOR = new ShapeGizmoEditor();
    public static final OrbitGizmoEditor ORBIT_GIZMO = new OrbitGizmoEditor();
    public static final CameraGizmoEditor CAMERA_GIZMO = new CameraGizmoEditor();
    public static final FocusPlaneGizmo FOCUS_GIZMO = new FocusPlaneGizmo();
    public static final PrefabPlacement PREFABS = new PrefabPlacement();
    public static final EditorCameraController EDITOR_CAMERA = new EditorCameraController();
    public static final PoseGizmoEditor POSE_GIZMO = new PoseGizmoEditor();

    private Editors() {}

    public static void clearAll() {
        GIZMO_EDITOR.clear();
        ORBIT_GIZMO.clear();
        CAMERA_GIZMO.clear();
        FOCUS_GIZMO.clear();
        POSE_GIZMO.clear();
        PREFABS.clear();
        ml.mypals.vectorthree.fb.camera.MotionPaths.clear();
        EDITOR_CAMERA.reset();
    }
}
