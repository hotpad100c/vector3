package ml.mypals.vectorthree.camera;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.line.LineShape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class CameraGizmoEditor {
    public record Pose(Vector3d position, float yaw, float pitch, float roll) {
        static Pose of(CameraKeyframe keyframe) {
            return new Pose(new Vector3d(keyframe.position), keyframe.yaw, keyframe.pitch, keyframe.roll);
        }

        Vec3 eye() {
            return new Vec3(position.x, position.y + eyeHeight(), position.z);
        }

        Vec3 forward() {
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        }

        Vec3 right() {
            Vec3 right = forward().cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1.0e-8) {
                double y = Math.toRadians(yaw);
                right = new Vec3(-Math.cos(y), 0, -Math.sin(y));
            }
            return right.normalize();
        }

        Vec3 up() {
            Vector3f up = right().cross(forward()).toVector3f()
                    .rotateAxis((float) Math.toRadians(-roll), (float) forward().x, (float) forward().y, (float) forward().z);
            return new Vec3(up.x, up.y, up.z);
        }

        Pose with(Vector3d position, float yaw, float pitch, float roll) {
            return new Pose(position, yaw, pitch, roll);
        }
    }

    private enum Kind { MOVE_X, MOVE_Y, MOVE_Z, MOVE_FREE, YAW, PITCH, ROLL }

    private record Handle(Kind kind, ObjModelShape shape, Color color) {}

    private static final Color X_COLOR = new Color(255, 55, 55, 230);
    private static final Color Y_COLOR = new Color(55, 255, 55, 230);
    private static final Color Z_COLOR = new Color(70, 100, 255, 230);
    private static final Color FREE_COLOR = new Color(255, 155, 35, 240);
    private static final Color YAW_COLOR = new Color(255, 210, 60, 235);
    private static final Color PITCH_COLOR = new Color(60, 220, 255, 235);
    private static final Color ROLL_COLOR = new Color(220, 90, 255, 235);
    private static final Color VIEW_COLOR = new Color(255, 255, 255, 220);
    private static final Color UP_COLOR = new Color(55, 255, 55, 200);
    private static final double RING_SCALE = 1.3;
    private static final float LINE_WIDTH = 3f;

    private final String session = UUID.randomUUID().toString();
    private final List<Handle> handles = new ArrayList<>();
    private LineShape viewLine;
    private LineShape upLine;

    private CameraKeyframe keyframe;
    private Consumer<Pose> commit = pose -> {};
    private Pose preview;
    private Handle hovered;
    private Handle dragging;
    private Pose dragStart;
    private Vec3 dragOrigin;
    private Vec3 dragAxis;
    private Vec3 dragPlaneStart;
    private double dragParameter;
    private double dragAngle;

    public boolean isDragging() {
        return dragging != null;
    }

    public boolean isHovering() {
        return hovered != null || dragging != null;
    }

    public void select(CameraKeyframe keyframe, Consumer<Pose> commit) {
        this.commit = commit;
        if (this.keyframe == keyframe) return;
        this.keyframe = keyframe;
        preview = null;
        dragging = null;
        ensureShapes();
        layout(Pose.of(keyframe));
    }

    public void clearSelection() {
        if (isDragging() || keyframe == null) return;
        clear();
    }

    public void clear() {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        if (viewLine != null) viewLine.discard();
        if (upLine != null) upLine.discard();
        viewLine = null;
        upLine = null;
        ShapeManagers.removeShapes(Vector3.id("camera_gizmo/" + session));
        keyframe = null;
        preview = null;
        hovered = null;
        dragging = null;
        commit = pose -> {};
    }

    public void frame() {
        if (!ReplayUI.isActive() || keyframe == null) return;
        if (dragging != null && ReplayUI.imguiWindower.isGrabbed()) ReplayUI.imguiWindower.ungrab();
        if (dragging != null && !ImGui.isMouseDown(1)) {
            if (preview != null) commit.accept(preview);
            preview = null;
            dragging = null;
            updateColors();
            return;
        }

        Pose pose = preview != null ? preview : Pose.of(keyframe);
        ensureShapes();
        layout(pose);

        Vec3 direction = ReplayUI.getMouseLookVector();
        if (direction == null && dragging != null) direction = ShapeGizmoEditor.unboundedMouseLookVector();
        if (direction == null) return;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        RayModelIntersection.Ray ray = new RayModelIntersection.Ray(camera.position(), direction);

        if (dragging == null) {
            setHovered(ShapeGizmoEditor.mouseInViewport() ? pick(ray) : null);
            if (ImGui.isMouseClicked(1) && hovered != null) {
                ReplayUI.imguiWindower.ungrab();
                beginDrag(hovered, pose, ray, camera);
            }
        }
        if (dragging != null && ImGui.isMouseDown(1)) {
            Pose replacement = drag(ray);
            if (replacement != null) {
                preview = replacement;
                layout(replacement);
            }
        }
    }

    private void ensureShapes() {
        if (viewLine != null) return;
        viewLine = line(VIEW_COLOR, "view");
        upLine = line(UP_COLOR, "up");
        addHandle(Kind.MOVE_X, ShapeGizmoEditor.MOVE_MODEL, X_COLOR);
        addHandle(Kind.MOVE_Y, ShapeGizmoEditor.MOVE_MODEL, Y_COLOR);
        addHandle(Kind.MOVE_Z, ShapeGizmoEditor.MOVE_MODEL, Z_COLOR);
        addHandle(Kind.MOVE_FREE, ShapeGizmoEditor.CENTER_MODEL, FREE_COLOR);
        addHandle(Kind.YAW, ShapeGizmoEditor.ROTATE_MODEL, YAW_COLOR);
        addHandle(Kind.PITCH, ShapeGizmoEditor.ROTATE_MODEL, PITCH_COLOR);
        addHandle(Kind.ROLL, ShapeGizmoEditor.ROTATE_MODEL, ROLL_COLOR);
    }

    private LineShape line(Color color, String name) {
        LineShape line = (LineShape) ShapeGenerator.generateLine().start(Vec3.ZERO).end(new Vec3(0, 1, 0))
                .lineWidth(LINE_WIDTH).color(color).seeThrough(true).build(Shape.RenderingType.BATCH);
        ShapeManagers.addShape(Vector3.id("camera_gizmo/" + session + "/" + name), line);
        return line;
    }

    private void addHandle(Kind kind, Identifier model, Color color) {
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {}, model, Vec3.ZERO, color, true);
        ShapeManagers.addShape(Vector3.id("camera_gizmo/" + session + "/handle/" + handles.size()), shape);
        handles.add(new Handle(kind, shape, color));
    }

    private void layout(Pose pose) {
        Vec3 eye = pose.eye();
        double scale = ShapeGizmoEditor.gizmoScale(eye);
        viewLine.forceSetStart(eye);
        viewLine.forceSetEnd(eye.add(pose.forward().scale(scale * 5)));
        upLine.forceSetStart(eye);
        upLine.forceSetEnd(eye.add(pose.up().scale(scale * 2)));
        for (Handle handle : handles) {
            double handleScale = switch (handle.kind()) {
                case YAW, PITCH, ROLL -> scale * RING_SCALE;
                case MOVE_FREE -> scale * 0.5;
                default -> scale;
            };
            Vector3f from = isRing(handle.kind()) ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vec3 direction = axis(pose, handle.kind());
            handle.shape().forceSetWorldPosition(eye);
            handle.shape().forceSetWorldScale(new Vec3(handleScale, handleScale, handleScale));
            handle.shape().forceSetWorldRotation(eulerDegrees(new Quaternionf().rotationTo(from, direction.toVector3f())));
        }
    }

    private static boolean isRing(Kind kind) {
        return kind == Kind.YAW || kind == Kind.PITCH || kind == Kind.ROLL;
    }

    private static Vec3 axis(Pose pose, Kind kind) {
        return switch (kind) {
            case MOVE_X -> new Vec3(1, 0, 0);
            case MOVE_Y, MOVE_FREE, YAW -> new Vec3(0, 1, 0);
            case MOVE_Z -> new Vec3(0, 0, 1);
            case PITCH -> pose.right();
            case ROLL -> pose.forward();
        };
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

    private void beginDrag(Handle handle, Pose pose, RayModelIntersection.Ray ray, Camera camera) {
        dragging = handle;
        dragStart = pose;
        dragOrigin = pose.eye();
        switch (handle.kind()) {
            case MOVE_FREE -> {
                dragAxis = new Vec3(camera.forwardVector());
                dragPlaneStart = ShapeGizmoEditor.intersectPlane(ray, dragOrigin, dragAxis);
            }
            case YAW, PITCH, ROLL -> {
                dragAxis = axis(pose, handle.kind());
                Vec3 point = ShapeGizmoEditor.intersectPlane(ray, dragOrigin, dragAxis);
                dragAngle = point == null ? 0 : ShapeGizmoEditor.angleOnPlane(point.subtract(dragOrigin), dragAxis);
            }
            default -> {
                dragAxis = axis(pose, handle.kind());
                dragParameter = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis);
            }
        }
        updateColors();
    }

    private Pose drag(RayModelIntersection.Ray ray) {
        boolean snap = InputHelper.isCtrlDownRaw();
        Pose start = dragStart;
        switch (dragging.kind()) {
            case MOVE_X, MOVE_Y, MOVE_Z -> {
                double delta = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
                return start.with(moved(start, dragAxis.scale(delta), snap), start.yaw(), start.pitch(), start.roll());
            }
            case MOVE_FREE -> {
                Vec3 current = ShapeGizmoEditor.intersectPlane(ray, dragOrigin, dragAxis);
                if (current == null || dragPlaneStart == null) return null;
                return start.with(moved(start, current.subtract(dragPlaneStart), snap), start.yaw(), start.pitch(), start.roll());
            }
            default -> {
                Vec3 point = ShapeGizmoEditor.intersectPlane(ray, dragOrigin, dragAxis);
                if (point == null) return null;
                double turned = Math.toDegrees(ShapeGizmoEditor.wrapAngle(
                        ShapeGizmoEditor.angleOnPlane(point.subtract(dragOrigin), dragAxis) - dragAngle));
                return switch (dragging.kind()) {
                    case YAW -> start.with(start.position(), angle(start.yaw(), turned, snap), start.pitch(), start.roll());
                    case PITCH -> start.with(start.position(), start.yaw(),
                            (float) Math.clamp(angle(start.pitch(), turned, snap), -90, 90), start.roll());
                    default -> start.with(start.position(), start.yaw(), start.pitch(), angle(start.roll(), turned, snap));
                };
            }
        }
    }

    private static Vector3d moved(Pose start, Vec3 delta, boolean snap) {
        Vector3d position = new Vector3d(start.position()).add(delta.x, delta.y, delta.z);
        if (snap) {
            position.set(ShapeGizmoEditor.snap(position.x, start.position().x, ShapeGizmoEditor.GRID_STEP, false),
                    ShapeGizmoEditor.snap(position.y, start.position().y, ShapeGizmoEditor.GRID_STEP, false),
                    ShapeGizmoEditor.snap(position.z, start.position().z, ShapeGizmoEditor.GRID_STEP, false));
        }
        return position;
    }

    private static float angle(float start, double turned, boolean snap) {
        double value = start - turned;
        if (snap) value = ShapeGizmoEditor.snap(value, start, ShapeGizmoEditor.ANGLE_STEP, false);
        return (float) value;
    }

    private static double eyeHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? 1.62 : minecraft.player.getEyeHeight();
    }

    private static Vector3f eulerDegrees(Quaternionf rotation) {
        Vector3f euler = rotation.getEulerAnglesXYZ(new Vector3f());
        return new Vector3f((float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y),
                (float) Math.toDegrees(euler.z));
    }
}
