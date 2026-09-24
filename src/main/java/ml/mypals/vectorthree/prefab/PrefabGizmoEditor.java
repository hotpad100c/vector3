package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Move / rotate / uniform-scale handles for a prefab's placement, dragged with the right mouse button. */
public final class PrefabGizmoEditor {
    public enum Mode { MOVE, ROTATE, SCALE }

    private enum Operation { MOVE_AXIS, MOVE_FREE, ROTATE, SCALE }

    private record Handle(Operation operation, int axis, ObjModelShape shape, Color color) {}

    private static final Color[] AXIS_COLORS = {new Color(255, 55, 55, 230), new Color(55, 255, 55, 230),
            new Color(70, 100, 255, 230)};
    private static final Color CENTER_COLOR = new Color(255, 155, 35, 240);
    private static final Vec3[] AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

    private final String session = UUID.randomUUID().toString();
    private final List<Handle> handles = new ArrayList<>();
    private Mode mode = Mode.MOVE;
    private Mode builtMode;
    private PrefabTransform transform = PrefabTransform.IDENTITY;
    private Handle hovered;
    private Handle dragging;
    private PrefabTransform dragStart;
    private Vec3 dragAxis;
    private Vec3 dragPlaneStart;
    private double dragParameter;
    private double dragAngle;

    public PrefabTransform transform() { return transform; }
    public void setTransform(PrefabTransform transform) { if (dragging == null) this.transform = transform; }
    public Mode mode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; dragging = null; }
    public boolean isDragging() { return dragging != null; }
    public boolean isHovering() { return hovered != null || dragging != null; }

    public void clear() {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        ShapeManagers.removeShapes(Vector3.id("prefab_gizmo/" + session));
        builtMode = null;
        hovered = null;
        dragging = null;
    }

    public void frame() {
        if (!ReplayUI.isActive()) return;
        if (builtMode != mode) rebuild();
        if (dragging != null && ReplayUI.imguiWindower.isGrabbed()) ReplayUI.imguiWindower.ungrab();
        if (dragging != null && !ImGui.isMouseDown(1)) {
            dragging = null;
            updateColors();
        }
        layout();

        Vec3 direction = ReplayUI.getMouseLookVector();
        if (direction == null && dragging != null) direction = ShapeGizmoEditor.unboundedMouseLookVector();
        if (direction == null) return;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        RayModelIntersection.Ray ray = new RayModelIntersection.Ray(camera.position(), direction);
        if (dragging == null) {
            setHovered(ShapeGizmoEditor.mouseInViewport() ? pick(ray) : null);
            if (ImGui.isMouseClicked(1) && hovered != null) {
                ReplayUI.imguiWindower.ungrab();
                beginDrag(hovered, ray, camera);
            }
        } else if (ImGui.isMouseDown(1)) {
            PrefabTransform dragged = drag(ray);
            if (dragged != null) transform = dragged;
        }
    }

    private void rebuild() {
        clear();
        builtMode = mode;
        switch (mode) {
            case MOVE -> {
                for (int axis = 0; axis < 3; axis++) add(Operation.MOVE_AXIS, axis, ShapeGizmoEditor.MOVE_MODEL, AXIS_COLORS[axis]);
                add(Operation.MOVE_FREE, -1, ShapeGizmoEditor.CENTER_MODEL, CENTER_COLOR);
            }
            case ROTATE -> {
                for (int axis = 0; axis < 3; axis++) add(Operation.ROTATE, axis, ShapeGizmoEditor.ROTATE_MODEL, AXIS_COLORS[axis]);
            }
            case SCALE -> add(Operation.SCALE, -1, ShapeGizmoEditor.SCALE_MODEL, CENTER_COLOR);
        }
    }

    private void add(Operation operation, int axis, Identifier model, Color color) {
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {}, model, Vec3.ZERO, color, true);
        ShapeManagers.addShape(Vector3.id("prefab_gizmo/" + session + "/" + handles.size()), shape);
        handles.add(new Handle(operation, axis, shape, color));
    }

    private void layout() {
        Vec3 center = center(transform);
        double scale = ShapeGizmoEditor.gizmoScale(center);
        for (Handle handle : handles) {
            handle.shape().forceSetWorldPosition(center);
            handle.shape().forceSetWorldScale(new Vec3(scale, scale, scale));
            handle.shape().forceSetWorldRotation(handleRotation(handle));
        }
    }

    // The models point along +Y (arrows) or lie in the XZ plane (rings), as in ShapeGizmoEditor.
    private static Vector3f handleRotation(Handle handle) {
        if (handle.operation() == Operation.ROTATE) {
            return handle.axis() == 1 ? new Vector3f(0, 0, 90) : handle.axis() == 2 ? new Vector3f(0, 90, 0) : new Vector3f();
        }
        return handle.axis() == 0 ? new Vector3f(0, 0, -90) : handle.axis() == 2 ? new Vector3f(90, 0, 0) : new Vector3f();
    }

    private Handle pick(RayModelIntersection.Ray ray) {
        Handle best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Handle handle : handles) {
            RayModelIntersection.HitResult hit = RayModelIntersection.rayIntersectsModel(
                    ray, handle.shape().getModel(false), handle.shape().indexBuffer);
            if (hit.hit && hit.distance < distance) {
                best = handle;
                distance = hit.distance;
            }
        }
        return best;
    }

    private void setHovered(Handle handle) {
        if (hovered == handle) return;
        hovered = handle;
        updateColors();
    }

    private void updateColors() {
        for (Handle handle : handles) {
            handle.shape().setBaseColor(handle == dragging ? ShapeGizmoEditor.ACTIVE_COLOR
                    : handle == hovered ? ShapeGizmoEditor.HOVER_COLOR : handle.color());
        }
    }

    private void beginDrag(Handle handle, RayModelIntersection.Ray ray, Camera camera) {
        dragging = handle;
        dragStart = transform;
        Vec3 center = center(transform);
        switch (handle.operation()) {
            case MOVE_AXIS -> {
                dragAxis = AXES[handle.axis()];
                dragParameter = ShapeGizmoEditor.axisParameter(ray, center, dragAxis);
            }
            case MOVE_FREE -> {
                dragAxis = new Vec3(camera.forwardVector());
                dragPlaneStart = ShapeGizmoEditor.intersectPlane(ray, center, dragAxis);
            }
            case ROTATE -> {
                dragAxis = AXES[handle.axis()];
                Vec3 point = ShapeGizmoEditor.intersectPlane(ray, center, dragAxis);
                dragAngle = point == null ? 0 : ShapeGizmoEditor.angleOnPlane(point.subtract(center), dragAxis);
            }
            case SCALE -> {
                dragAxis = new Vec3(camera.leftVector()).scale(-1);
                dragParameter = ShapeGizmoEditor.axisParameter(ray, center, dragAxis);
            }
        }
        updateColors();
    }

    private PrefabTransform drag(RayModelIntersection.Ray ray) {
        boolean snap = InputHelper.isCtrlDownRaw();
        Vec3 origin = center(dragStart);
        Vector3d center = new Vector3d(dragStart.center());
        Vector3f rotation = new Vector3f(dragStart.rotationDegrees());
        double scale = dragStart.scale();
        switch (dragging.operation()) {
            case MOVE_AXIS -> {
                double delta = ShapeGizmoEditor.axisParameter(ray, origin, dragAxis) - dragParameter;
                center.add(dragAxis.x * delta, dragAxis.y * delta, dragAxis.z * delta);
            }
            case MOVE_FREE -> {
                Vec3 current = ShapeGizmoEditor.intersectPlane(ray, origin, dragAxis);
                if (current == null || dragPlaneStart == null) return null;
                Vec3 delta = current.subtract(dragPlaneStart);
                center.add(delta.x, delta.y, delta.z);
            }
            case ROTATE -> {
                Vec3 point = ShapeGizmoEditor.intersectPlane(ray, origin, dragAxis);
                if (point == null) return null;
                float delta = (float) Math.toDegrees(ShapeGizmoEditor.wrapAngle(
                        ShapeGizmoEditor.angleOnPlane(point.subtract(origin), dragAxis) - dragAngle));
                rotation.setComponent(dragging.axis(), rotation.get(dragging.axis()) + delta);
                if (snap) rotation.setComponent(dragging.axis(), (float) ShapeGizmoEditor.snap(
                        rotation.get(dragging.axis()), dragStart.rotationDegrees().get(dragging.axis()), ShapeGizmoEditor.ANGLE_STEP, false));
            }
            case SCALE -> {
                double delta = ShapeGizmoEditor.axisParameter(ray, origin, dragAxis) - dragParameter;
                scale = dragStart.scale() * Math.max(0.001, 1 + delta / Math.max(0.05, ShapeGizmoEditor.gizmoScale(origin) * 3));
                if (snap) scale = ShapeGizmoEditor.snap(scale, dragStart.scale(), 0.25, true);
            }
        }
        if (snap && dragging.operation() != Operation.ROTATE && dragging.operation() != Operation.SCALE) {
            Vector3d before = dragStart.center();
            center.set(ShapeGizmoEditor.snap(center.x, before.x, ShapeGizmoEditor.GRID_STEP, false),
                    ShapeGizmoEditor.snap(center.y, before.y, ShapeGizmoEditor.GRID_STEP, false),
                    ShapeGizmoEditor.snap(center.z, before.z, ShapeGizmoEditor.GRID_STEP, false));
        }
        return new PrefabTransform(center, rotation, scale);
    }

    private static Vec3 center(PrefabTransform transform) {
        Vector3d center = transform.center();
        return new Vec3(center.x, center.y, center.z);
    }
}
