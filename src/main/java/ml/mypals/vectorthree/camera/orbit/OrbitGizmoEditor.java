package ml.mypals.vectorthree.camera.orbit;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.line.LineShape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shape.round.LineCircleShape;
import ml.mypals.ryansrenderingkit.shape.round.SphereShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;


public final class OrbitGizmoEditor {
    public record Orbit(Vector3d center, double distance, double yaw, double pitch, double tiltX, double tiltZ) {
        static Orbit of(CameraOrbitKeyframe keyframe) {
            OrbitTilt tilt = (OrbitTilt) keyframe;
            return new Orbit(new Vector3d(keyframe.center), keyframe.distance, keyframe.yaw, keyframe.pitch,
                    tilt.vector3$tiltX(), tilt.vector3$tiltZ());
        }

        Quaterniond frame() { return OrbitMath.tilt(tiltX, tiltZ); }
        Vector3d offset() { return OrbitMath.eyeOffset(yaw, pitch, distance, tiltX, tiltZ); }
        Vector3d eye() { return new Vector3d(center).add(offset()); }
        Vector3d normal() { return frame().transform(new Vector3d(0, 1, 0)); }

        Vector3d yawTangent() {
            double y = Math.toRadians(yaw);
            return frame().transform(new Vector3d(Math.cos(y), 0, Math.sin(y))).normalize();
        }

        Vector3d pitchTangent() {
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            return frame().transform(new Vector3d(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p)))
                    .normalize();
        }

        Orbit with(Vector3d center, double distance, double yaw, double pitch, double tiltX, double tiltZ) {
            return new Orbit(center, distance, yaw, pitch, tiltX, tiltZ);
        }
    }

    private enum Kind { CENTER_X, CENTER_Y, CENTER_Z, YAW, PITCH, DISTANCE, TILT }

    private record Handle(Kind kind, ObjModelShape shape, Color color) {}

    private static final Color X_COLOR = new Color(255, 55, 55, 230);
    private static final Color Y_COLOR = new Color(55, 255, 55, 230);
    private static final Color Z_COLOR = new Color(70, 100, 255, 230);
    private static final Color YAW_COLOR = new Color(255, 210, 60, 235);
    private static final Color PITCH_COLOR = new Color(60, 220, 255, 235);
    private static final Color DISTANCE_COLOR = new Color(255, 155, 35, 235);
    private static final Color TILT_COLOR = new Color(220, 90, 255, 235);
    private static final Color RING_COLOR = new Color(255, 255, 255, 210);
    private static final Color SPOKE_COLOR = new Color(200, 200, 200, 160);
    private static final float LINE_WIDTH = 3f;
    private static final double MIN_DISTANCE = 0.1;
    private static final double MAX_PITCH = 89.9;

    private final String session = UUID.randomUUID().toString();
    private final List<Handle> handles = new ArrayList<>();
    private LineCircleShape ring;
    private LineShape spoke;
    private SphereShape cameraBall;
    private SphereShape distanceBall;
    private ObjModelShape centerMarker;

    private CameraOrbitKeyframe keyframe;
    private Consumer<Orbit> commit = orbit -> {};
    private Orbit preview;
    private Handle hovered;
    private Handle dragging;
    private Orbit dragStart;
    private Vec3 dragOrigin;
    private Vec3 dragAxis;
    private double dragParameter;
    private double dragAngle;

    public boolean isDragging() {
        return dragging != null;
    }

    public boolean isHovering() {
        return hovered != null || dragging != null;
    }

    public void select(CameraOrbitKeyframe keyframe, Consumer<Orbit> commit) {
        this.commit = commit;
        if (this.keyframe == keyframe) return;
        this.keyframe = keyframe;
        preview = null;
        dragging = null;
        ensureShapes();
        layout(Orbit.of(keyframe));
    }

    public void clearSelection() {
        if (isDragging() || keyframe == null) return;
        clear();
    }

    public void clear() {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        for (Shape shape : new Shape[]{ring, spoke, cameraBall, distanceBall, centerMarker}) {
            if (shape != null) shape.discard();
        }
        ring = null;
        spoke = null;
        cameraBall = null;
        distanceBall = null;
        centerMarker = null;
        ShapeManagers.removeShapes(Vector3.id("orbit_gizmo/" + session));
        keyframe = null;
        preview = null;
        hovered = null;
        dragging = null;
        commit = orbit -> {};
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

        Orbit orbit = preview != null ? preview : Orbit.of(keyframe);
        ensureShapes();
        layout(orbit);

        Vec3 direction = ReplayUI.getMouseLookVector();
        if (direction == null && dragging != null) direction = ShapeGizmoEditor.unboundedMouseLookVector();
        if (direction == null) return;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        RayModelIntersection.Ray ray = new RayModelIntersection.Ray(camera.position(), direction);

        if (dragging == null) {
            setHovered(ShapeGizmoEditor.mouseInViewport() ? pick(ray) : null);
            if (ImGui.isMouseClicked(1) && hovered != null) {
                ReplayUI.imguiWindower.ungrab();
                beginDrag(hovered, orbit, ray);
            }
        }
        if (dragging != null && ImGui.isMouseDown(1)) {
            Orbit replacement = drag(ray);
            if (replacement != null) {
                preview = replacement;
                layout(replacement);
            }
        }
    }

    private void ensureShapes() {
        if (ring != null) return;
        ring = (LineCircleShape) ShapeGenerator.generateLineCircle().radius(1).segments(96).lineWidth(LINE_WIDTH)
                .color(RING_COLOR).seeThrough(true).build(Shape.RenderingType.BATCH);
        spoke = (LineShape) ShapeGenerator.generateLine().start(Vec3.ZERO).end(new Vec3(0, 1, 0)).lineWidth(LINE_WIDTH)
                .color(SPOKE_COLOR).seeThrough(true).build(Shape.RenderingType.BATCH);
        cameraBall = ball(YAW_COLOR);
        distanceBall = ball(DISTANCE_COLOR);
        centerMarker = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                ShapeGizmoEditor.CENTER_MODEL, Vec3.ZERO, RING_COLOR, true);
        addVisual("ring", ring);
        addVisual("spoke", spoke);
        addVisual("camera", cameraBall);
        addVisual("distance", distanceBall);
        addVisual("center", centerMarker);
        addHandle(Kind.CENTER_X, X_COLOR);
        addHandle(Kind.CENTER_Y, Y_COLOR);
        addHandle(Kind.CENTER_Z, Z_COLOR);
        addHandle(Kind.YAW, YAW_COLOR);
        addHandle(Kind.PITCH, PITCH_COLOR);
        addHandle(Kind.DISTANCE, DISTANCE_COLOR);
        addHandle(Kind.TILT, TILT_COLOR);
    }

    private static SphereShape ball(Color color) {
        return (SphereShape) ShapeGenerator.generateSphere().radius(1).segments(12).color(color).seeThrough(true)
                .build(Shape.RenderingType.BATCH);
    }

    private void addVisual(String name, Shape shape) {
        ShapeManagers.addShape(Vector3.id("orbit_gizmo/" + session + "/" + name), shape);
    }

    private void addHandle(Kind kind, Color color) {
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                ShapeGizmoEditor.MOVE_MODEL, Vec3.ZERO, color, true);
        ShapeManagers.addShape(Vector3.id("orbit_gizmo/" + session + "/handle/" + handles.size()), shape);
        handles.add(new Handle(kind, shape, color));
    }

    private void layout(Orbit orbit) {
        Vec3 center = vec(orbit.center());
        Vec3 eye = vec(orbit.eye());
        Vec3 distancePoint = distanceHandlePosition(orbit);
        Quaterniond frame = orbit.frame();
        double pitch = Math.toRadians(orbit.pitch());

        Vector3d ringCenter = new Vector3d(orbit.center())
                .add(frame.transform(new Vector3d(0, orbit.distance() * Math.sin(pitch), 0)));
        ring.forceSetWorldPosition(vec(ringCenter));
        ring.forceSetWorldRotation(eulerDegrees(new Quaternionf(frame)));
        ring.forceSetRadius((float) Math.max(0.01, orbit.distance() * Math.cos(pitch)));
        spoke.forceSetStart(center);
        spoke.forceSetEnd(eye);

        placeBall(cameraBall, eye, 0.2);
        placeBall(distanceBall, distancePoint, 0.16);
        double markerScale = ShapeGizmoEditor.gizmoScale(center) * 0.5;
        centerMarker.forceSetWorldPosition(center);
        centerMarker.forceSetWorldScale(new Vec3(markerScale, markerScale, markerScale));

        for (Handle handle : handles) {
            Vec3 position = switch (handle.kind()) {
                case CENTER_X, CENTER_Y, CENTER_Z -> center;
                case YAW, PITCH -> eye;
                case DISTANCE, TILT -> distancePoint;
            };
            double scale = ShapeGizmoEditor.gizmoScale(position);
            handle.shape().forceSetWorldPosition(position);
            handle.shape().forceSetWorldScale(new Vec3(scale, scale, scale));
            handle.shape().forceSetWorldRotation(eulerDegrees(new Quaternionf()
                    .rotationTo(new Vector3f(0, 1, 0), handleDirection(orbit, handle.kind()).toVector3f())));
        }
    }

    private static void placeBall(SphereShape ball, Vec3 position, double size) {
        float radius = (float) (ShapeGizmoEditor.gizmoScale(position) * size);
        ball.forceSetWorldPosition(position);
        ball.setRadius(radius);
        ball.transformer.syncLastToTarget();
        ball.generateSphereShape(false);
    }

    /** Just beyond the camera along the center-to-camera line. */
    private static Vec3 distanceHandlePosition(Orbit orbit) {
        Vec3 eye = vec(orbit.eye());
        Vector3d outward = new Vector3d(orbit.offset());
        if (outward.lengthSquared() < 1.0e-9) outward.set(0, 0, 1);
        outward.normalize().mul(ShapeGizmoEditor.gizmoScale(eye) * 1.4);
        return eye.add(outward.x, outward.y, outward.z);
    }

    private static Vec3 handleDirection(Orbit orbit, Kind kind) {
        return switch (kind) {
            case CENTER_X -> new Vec3(1, 0, 0);
            case CENTER_Y -> new Vec3(0, 1, 0);
            case CENTER_Z -> new Vec3(0, 0, 1);
            case YAW -> vec(orbit.yawTangent());
            case PITCH, TILT -> vec(orbit.pitchTangent());
            case DISTANCE -> {
                Vector3d offset = orbit.offset();
                yield offset.lengthSquared() < 1.0e-9 ? new Vec3(0, 0, 1) : vec(offset.normalize());
            }
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

    private void beginDrag(Handle handle, Orbit orbit, RayModelIntersection.Ray ray) {
        dragging = handle;
        dragStart = orbit;
        Vec3 center = vec(orbit.center());
        switch (handle.kind()) {
            case CENTER_X, CENTER_Y, CENTER_Z -> {
                dragOrigin = center;
                dragAxis = handleDirection(orbit, handle.kind());
                dragParameter = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis);
            }
            case DISTANCE -> {
                dragOrigin = center;
                dragAxis = handleDirection(orbit, Kind.DISTANCE);
                dragParameter = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis);
            }
            case TILT -> {
                // The ring tilts about the axis along it at the camera, through the center.
                dragOrigin = center;
                dragAxis = vec(orbit.yawTangent());
                Vec3 point = ShapeGizmoEditor.intersectPlane(ray, dragOrigin, dragAxis);
                dragAngle = point == null ? 0 : ShapeGizmoEditor.angleOnPlane(point.subtract(dragOrigin), dragAxis);
            }
            case YAW, PITCH -> dragOrigin = center;
        }
        updateColors();
    }

    private Orbit drag(RayModelIntersection.Ray ray) {
        boolean snap = InputHelper.isCtrlDownRaw();
        Orbit start = dragStart;
        switch (dragging.kind()) {
            case CENTER_X, CENTER_Y, CENTER_Z -> {
                double delta = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
                Vector3d center = new Vector3d(start.center()).add(dragAxis.x * delta, dragAxis.y * delta, dragAxis.z * delta);
                if (snap) center.set(snapGrid(center.x, start.center().x), snapGrid(center.y, start.center().y),
                        snapGrid(center.z, start.center().z));
                return start.with(center, start.distance(), start.yaw(), start.pitch(), start.tiltX(), start.tiltZ());
            }
            case DISTANCE -> {
                double delta = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
                double distance = Math.max(MIN_DISTANCE, start.distance() + delta);
                if (snap) distance = ShapeGizmoEditor.snap(distance, start.distance(), ShapeGizmoEditor.GRID_STEP, true);
                return start.with(start.center(), distance, start.yaw(), start.pitch(), start.tiltX(), start.tiltZ());
            }
            case YAW -> {
                // Wherever the mouse ray meets the ring's plane, the camera goes to that azimuth.
                Vector3d ringCenter = new Vector3d(start.center()).add(start.frame()
                        .transform(new Vector3d(0, start.distance() * Math.sin(Math.toRadians(start.pitch())), 0)));
                Vec3 hit = ShapeGizmoEditor.intersectPlane(ray, vec(ringCenter), vec(start.normal()));
                if (hit == null) return null;
                double yaw = OrbitMath.orbitOf(offsetTo(start, hit), start.tiltX(), start.tiltZ(), start.yaw())[0];
                if (snap) yaw = ShapeGizmoEditor.snap(yaw, start.yaw(), ShapeGizmoEditor.ANGLE_STEP, false);
                return start.with(start.center(), start.distance(), yaw, start.pitch(), start.tiltX(), start.tiltZ());
            }
            case PITCH -> {
                // Meridian plane through the center: the elevation of the mouse ray's hit becomes the pitch.
                Vec3 hit = ShapeGizmoEditor.intersectPlane(ray, dragOrigin, vec(start.yawTangent()));
                if (hit == null) return null;
                double pitch = OrbitMath.orbitOf(offsetTo(start, hit), start.tiltX(), start.tiltZ(), start.yaw())[1];
                pitch = Math.clamp(pitch, -MAX_PITCH, MAX_PITCH);
                if (snap) pitch = ShapeGizmoEditor.snap(pitch, start.pitch(), ShapeGizmoEditor.ANGLE_STEP, false);
                return start.with(start.center(), start.distance(), start.yaw(), pitch, start.tiltX(), start.tiltZ());
            }
            case TILT -> {
                Vec3 hit = ShapeGizmoEditor.intersectPlane(ray, dragOrigin, dragAxis);
                if (hit == null) return null;
                double delta = ShapeGizmoEditor.wrapAngle(
                        ShapeGizmoEditor.angleOnPlane(hit.subtract(dragOrigin), dragAxis) - dragAngle);
                Quaterniond rotated = new Quaterniond().rotateAxis(delta, dragAxis.x, dragAxis.y, dragAxis.z)
                        .mul(start.frame());
                double[] tilt = OrbitMath.tiltOf(rotated.transform(new Vector3d(0, 1, 0)));
                if (snap) {
                    tilt[0] = ShapeGizmoEditor.snap(tilt[0], start.tiltX(), ShapeGizmoEditor.ANGLE_STEP, false);
                    tilt[1] = ShapeGizmoEditor.snap(tilt[1], start.tiltZ(), ShapeGizmoEditor.ANGLE_STEP, false);
                }
                // Keep the camera where the tilted ring carried it: re-express its position in the new frame.
                Vector3d carried = rotated.transform(OrbitMath.flatOffset(start.yaw(), start.pitch(), start.distance()));
                double[] orbit = OrbitMath.orbitOf(carried, tilt[0], tilt[1], start.yaw());
                return start.with(start.center(), start.distance(), orbit[0],
                        Math.clamp(orbit[1], -MAX_PITCH, MAX_PITCH), tilt[0], tilt[1]);
            }
        }
        return null;
    }

    private static double snapGrid(double value, double before) {
        return ShapeGizmoEditor.snap(value, before, ShapeGizmoEditor.GRID_STEP, false);
    }

    private static Vector3d offsetTo(Orbit orbit, Vec3 point) {
        return new Vector3d(point.x, point.y, point.z).sub(orbit.center());
    }

    private static Vec3 vec(Vector3d value) {
        return new Vec3(value.x, value.y, value.z);
    }

    private static Vector3f eulerDegrees(Quaternionf rotation) {
        Vector3f euler = rotation.getEulerAnglesXYZ(new Vector3f());
        return new Vector3f((float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y),
                (float) Math.toDegrees(euler.z));
    }
}
