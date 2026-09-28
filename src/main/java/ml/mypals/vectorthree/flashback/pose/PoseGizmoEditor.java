package ml.mypals.vectorthree.flashback.pose;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.shape.GizmoMode;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Viewport gizmo for an Entity Pose keyframe: a marker on every part's pivot (right-click one to pick it) and, on the
 * picked part, one ring per rotation and one arrow per offset axis (R / G pick which). Each ring turns exactly one of
 * the part's X / Y / Z angles, about the axis that angle really turns about, so keyframes keep blending angle by angle.
 * Right-drag a handle; Ctrl snaps.
 * <p>
 * The part frames come from the renderer (see EntityPoses#modelSubmitted), which reports them for the selected
 * entity each frame.
 */
public final class PoseGizmoEditor {
    private enum Axis { X, Y, Z }

    private record Ring(Axis axis, boolean move, ObjModelShape shape, Color color) {}

    private record Marker(String part, ObjModelShape shape) {}

    private static final Color X_COLOR = new Color(255, 55, 55, 230);
    private static final Color Y_COLOR = new Color(55, 255, 55, 230);
    private static final Color Z_COLOR = new Color(70, 100, 255, 230);
    private static final Color MARKER_COLOR = new Color(255, 155, 35, 200);
    private static final Color SELECTED_COLOR = new Color(255, 255, 255, 240);
    private static final double RING_SCALE = 1.0;
    private static final double MARKER_SCALE = 0.35;
    // Markers are picked by how close the mouse ray passes to the pivot, in gizmo units (about 20px on screen).
    private static final double MARKER_PICK_RADIUS = 0.3;

    private final String session = UUID.randomUUID().toString();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Marker> markers = new ArrayList<>();

    private CustomKeyframe<EntityPose> keyframe;
    private Consumer<EntityPose> commit = pose -> {};
    private volatile Map<String, EntityPoses.Frame> frames = Map.of();
    private @Nullable String part;
    private @Nullable EntityPose preview;
    private Object hovered;
    private Ring dragging;
    private EntityPose dragStart;
    private Vec3 dragPivot;
    private Vec3 dragAxis;
    private boolean dragMirrored;
    private Vec3 dragFrom;
    private double dragParameter;
    private double dragUnit;

    public boolean isDragging() {
        return dragging != null;
    }

    public boolean isHovering() {
        return hovered != null || dragging != null;
    }

    /** The entity whose part frames the renderer should report, or null. */
    public @Nullable UUID entity() {
        CustomKeyframe<EntityPose> selected = keyframe;
        return selected == null ? null : (preview != null ? preview : selected.value).entity();
    }

    public void reportFrames(Model<?> model, Matrix4f entityMatrix, Vec3 camera) {
        frames = EntityPoses.frames(model, entityMatrix, camera);
    }

    /** The part the panel or a marker picked; its rings are shown. */
    public @Nullable String part() {
        return part;
    }

    public void focus(String part) {
        this.part = part;
        rebuildMarkers();
    }

    public void select(CustomKeyframe<EntityPose> keyframe, Consumer<EntityPose> commit) {
        this.commit = commit;
        if (this.keyframe == keyframe) return;
        UUID previous = entity();
        this.keyframe = keyframe;
        if (!java.util.Objects.equals(previous, entity())) {
            frames = Map.of();
            rebuildMarkers();
        }
        preview = null;
        dragging = null;
    }

    public void clearSelection() {
        if (isDragging() || keyframe == null) return;
        clear();
    }

    public void clear() {
        EntityPoses.clearPreview(entity());
        discardShapes();
        keyframe = null;
        frames = Map.of();
        preview = null;
        hovered = null;
        dragging = null;
        commit = pose -> {};
    }

    private void discardShapes() {
        for (Ring ring : rings) ring.shape().discard();
        for (Marker marker : markers) marker.shape().discard();
        rings.clear();
        markers.clear();
        ShapeManagers.removeShapes(Vector3.id("pose_gizmo/" + session));
    }

    public void frame() {
        if (!ReplayUI.isActive() || keyframe == null) return;
        if (dragging != null && ReplayUI.imguiWindower.isGrabbed()) ReplayUI.imguiWindower.ungrab();
        if (dragging != null && !ImGui.isMouseDown(1)) {
            if (preview != null) commit.accept(preview);
            EntityPoses.clearPreview(entity());
            preview = null;
            dragging = null;
            updateColors();
            return;
        }
        Map<String, EntityPoses.Frame> frames = this.frames;
        if (frames.isEmpty()) {
            discardShapes();
            return;
        }
        if (part != null && !frames.containsKey(part)) part = null;
        if (markers.size() != frames.size()) rebuildMarkers();
        layout(frames);

        Vec3 direction = ReplayUI.getMouseLookVector();
        if (direction == null && dragging != null) direction = ShapeGizmoEditor.unboundedMouseLookVector();
        if (direction == null) return;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        RayModelIntersection.Ray ray = new RayModelIntersection.Ray(camera.position(), direction);

        if (dragging == null) {
            setHovered(ShapeGizmoEditor.mouseInViewport() ? pick(ray) : null);
            if (ImGui.isMouseClicked(1) && hovered instanceof Marker marker) {
                focus(marker.part());
            } else if (ImGui.isMouseClicked(1) && hovered instanceof Ring ring && part != null) {
                ReplayUI.imguiWindower.ungrab();
                beginDrag(ring, frames.get(part), ray);
            }
        }
        if (dragging != null && ImGui.isMouseDown(1)) {
            EntityPose replacement = drag(ray);
            if (replacement != null) {
                preview = replacement;
                EntityPoses.preview(replacement.entity(), keyframe.value, replacement);
            }
        }
    }

    private void rebuildMarkers() {
        discardShapes();
        Map<String, EntityPoses.Frame> frames = this.frames;
        if (frames.isEmpty()) return;
        for (String name : frames.keySet()) {
            ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                    ShapeGizmoEditor.CENTER_MODEL, Vec3.ZERO, name.equals(part) ? SELECTED_COLOR : MARKER_COLOR, true);
            ShapeManagers.addShape(Vector3.id("pose_gizmo/" + session + "/marker/" + markers.size()), shape);
            markers.add(new Marker(name, shape));
        }
        for (boolean move : new boolean[]{false, true}) {
            addRing(Axis.X, move, X_COLOR);
            addRing(Axis.Y, move, Y_COLOR);
            addRing(Axis.Z, move, Z_COLOR);
        }
    }

    private void addRing(Axis axis, boolean move, Color color) {
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                move ? ShapeGizmoEditor.MOVE_MODEL : ShapeGizmoEditor.ROTATE_MODEL, Vec3.ZERO, color, true);
        ShapeManagers.addShape(Vector3.id("pose_gizmo/" + session + "/" + (move ? "arrow/" : "ring/") + axis), shape);
        rings.add(new Ring(axis, move, shape, color));
    }

    private boolean shown(Ring ring) {
        if (dragging != null) return dragging == ring;
        return switch (GizmoMode.current()) {
            case MOVE -> ring.move();
            case ROTATE -> !ring.move();
            default -> true;
        };
    }

    private void layout(Map<String, EntityPoses.Frame> frames) {
        for (Marker marker : markers) {
            EntityPoses.Frame frame = frames.get(marker.part());
            if (frame == null) continue;
            double scale = ShapeGizmoEditor.gizmoScale(frame.pivot()) * MARKER_SCALE;
            marker.shape().forceSetWorldPosition(frame.pivot());
            marker.shape().forceSetWorldScale(new Vec3(scale, scale, scale));
        }
        EntityPoses.Frame selected = part == null ? null : frames.get(part);
        for (Ring ring : rings) {
            if (selected == null || !shown(ring)) {
                ring.shape().disable();
                continue;
            }
            ring.shape().enable();
            double scale = ShapeGizmoEditor.gizmoScale(selected.pivot()) * RING_SCALE;
            ring.shape().forceSetWorldPosition(selected.pivot());
            ring.shape().forceSetWorldScale(new Vec3(scale, scale, scale));
            Vector3f axis = (ring.move() ? moveAxis(selected, ring.axis()).normalize() : axis(selected, ring.axis())).toVector3f();
            Vector3f from = ring.move() ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            ring.shape().forceSetWorldRotation(eulerDegrees(new Quaternionf().rotationTo(from, axis)));
        }
    }

    private static Vec3 axis(EntityPoses.Frame frame, Axis axis) {
        return switch (axis) {
            case X -> frame.xAxis();
            case Y -> frame.yAxis();
            case Z -> frame.zAxis();
        };
    }

    private static Vec3 moveAxis(EntityPoses.Frame frame, Axis axis) {
        return switch (axis) {
            case X -> frame.moveX();
            case Y -> frame.moveY();
            case Z -> frame.moveZ();
        };
    }

    private Object pick(RayModelIntersection.Ray ray) {
        Object best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Ring ring : rings) {
            if (!ring.shape().enabled()) continue;
            RayModelIntersection.HitResult hit = RayModelIntersection.rayIntersectsModel(
                    ray, ring.shape().getModel(false), ring.shape().indexBuffer);
            if (hit.hit && hit.distance < distance) {
                best = ring;
                distance = hit.distance;
            }
        }
        if (best != null) return best;
        Map<String, EntityPoses.Frame> frames = this.frames;
        double closest = Double.POSITIVE_INFINITY;
        for (Marker marker : markers) {
            EntityPoses.Frame frame = frames.get(marker.part());
            if (frame == null) continue;
            Vec3 toPivot = frame.pivot().subtract(ray.origin);
            double along = toPivot.dot(ray.direction);
            if (along <= 0) continue;
            double miss = toPivot.subtract(ray.direction.scale(along)).length()
                    / ShapeGizmoEditor.gizmoScale(frame.pivot());
            if (miss < MARKER_PICK_RADIUS && miss < closest) {
                best = marker;
                closest = miss;
            }
        }
        return best;
    }

    private void setHovered(Object target) {
        if (hovered == target) return;
        hovered = target;
        updateColors();
    }

    private void updateColors() {
        for (Ring ring : rings) {
            ring.shape().setBaseColor(ring == dragging ? ShapeGizmoEditor.ACTIVE_COLOR
                    : ring == hovered ? ShapeGizmoEditor.HOVER_COLOR : ring.color());
        }
        for (Marker marker : markers) {
            marker.shape().setBaseColor(marker == hovered ? ShapeGizmoEditor.HOVER_COLOR
                    : marker.part().equals(part) ? SELECTED_COLOR : MARKER_COLOR);
        }
    }

    private void beginDrag(Ring ring, EntityPoses.Frame frame, RayModelIntersection.Ray ray) {
        if (frame == null) return;
        dragging = ring;
        if (ring.move()) {
            dragStart = enabledOffset(keyframe.value, frame);
            dragPivot = frame.pivot();
            Vec3 step = moveAxis(frame, ring.axis());
            dragUnit = Math.max(1.0e-6, step.length());
            dragAxis = step.scale(1 / dragUnit);
            dragParameter = ShapeGizmoEditor.axisParameter(ray, dragPivot, dragAxis);
            updateColors();
            return;
        }
        dragStart = enabledPart(keyframe.value, frame);
        dragPivot = frame.pivot();
        dragAxis = axis(frame, ring.axis());
        dragMirrored = frame.mirrored();
        Vec3 point = ShapeGizmoEditor.intersectPlane(ray, dragPivot, dragAxis);
        dragFrom = point == null ? null : point.subtract(dragPivot);
        updateColors();
    }

    // A part whose rotation isn't on yet starts from where it is drawn now (or from zero when adding on top).
    private EntityPose enabledPart(EntityPose pose, EntityPoses.Frame frame) {
        EntityPose.Limb limb = pose.parts().getOrDefault(part, EntityPose.Limb.NONE);
        if (limb.rotate()) return pose;
        EntityPose.Limb start = pose.mode() == EntityPose.Mode.ADDITIVE ? limb.withRotation(0, 0, 0)
                : limb.withRotation(frame.xRot(), frame.yRot(), frame.zRot());
        return pose.withPart(part, start.withRotate(true));
    }

    private EntityPose enabledOffset(EntityPose pose, EntityPoses.Frame frame) {
        EntityPose.Limb limb = pose.parts().getOrDefault(part, EntityPose.Limb.NONE);
        if (limb.move()) return pose;
        EntityPose.Limb start = pose.mode() == EntityPose.Mode.ADDITIVE ? limb.withOffset(0, 0, 0)
                : limb.withOffset(frame.x(), frame.y(), frame.z());
        return pose.withPart(part, start.withMove(true));
    }

    private EntityPose dragOffset(RayModelIntersection.Ray ray) {
        double moved = (ShapeGizmoEditor.axisParameter(ray, dragPivot, dragAxis) - dragParameter) / dragUnit;
        EntityPose.Limb limb = dragStart.parts().get(part);
        float start = switch (dragging.axis()) {
            case X -> limb.x();
            case Y -> limb.y();
            case Z -> limb.z();
        };
        double value = start + moved;
        if (InputHelper.isCtrlDownRaw()) value = ShapeGizmoEditor.snap(value, start, 1, false);
        float offset = (float) value;
        EntityPose.Limb movedLimb = switch (dragging.axis()) {
            case X -> limb.withOffset(offset, limb.y(), limb.z());
            case Y -> limb.withOffset(limb.x(), offset, limb.z());
            case Z -> limb.withOffset(limb.x(), limb.y(), offset);
        };
        return dragStart.withPart(part, movedLimb);
    }

    private @Nullable EntityPose drag(RayModelIntersection.Ray ray) {
        if (dragging.move()) return dragOffset(ray);
        Vec3 point = ShapeGizmoEditor.intersectPlane(ray, dragPivot, dragAxis);
        if (point == null || dragFrom == null) return null;
        Vec3 to = point.subtract(dragPivot);
        // Right-handed angle about the world axis; a mirrored model frame turns the other way.
        double turned = Math.toDegrees(Math.atan2(dragFrom.cross(to).dot(dragAxis), dragFrom.dot(to)));
        if (dragMirrored) turned = -turned;
        EntityPose.Limb limb = dragStart.parts().get(part);
        float start = switch (dragging.axis()) {
            case X -> limb.xRot();
            case Y -> limb.yRot();
            case Z -> limb.zRot();
        };
        double value = start + turned;
        if (InputHelper.isCtrlDownRaw()) value = ShapeGizmoEditor.snap(value, start, ShapeGizmoEditor.ANGLE_STEP, false);
        float angle = (float) value;
        EntityPose.Limb turnedLimb = switch (dragging.axis()) {
            case X -> limb.withRotation(angle, limb.yRot(), limb.zRot());
            case Y -> limb.withRotation(limb.xRot(), angle, limb.zRot());
            case Z -> limb.withRotation(limb.xRot(), limb.yRot(), angle);
        };
        return dragStart.withPart(part, turnedLimb);
    }

    private static Vector3f eulerDegrees(Quaternionf rotation) {
        Vector3f euler = rotation.getEulerAnglesXYZ(new Vector3f());
        return new Vector3f((float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z));
    }
}
