package ml.mypals.vectorthree.shape;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class ShapeGizmoEditor implements ShapeTrackEditor {
    private static final Identifier MOVE_MODEL = Vector3.id("models/obj/move.obj");
    private static final Identifier ROTATE_MODEL = Vector3.id("models/obj/rotation.obj");
    private static final Identifier SCALE_MODEL = Vector3.id("models/obj/scale.obj");
    private static final Identifier CENTER_MODEL = Vector3.id("models/obj/m_center.obj");
    private static final Color X_COLOR = new Color(255, 55, 55, 230);
    private static final Color Y_COLOR = new Color(55, 255, 55, 230);
    private static final Color Z_COLOR = new Color(70, 100, 255, 230);
    private static final Color PROPERTY_COLOR = new Color(255, 155, 35, 240);
    private static final Color HOVER_COLOR = new Color(255, 235, 40, 255);
    private static final Color ACTIVE_COLOR = Color.WHITE;

    private enum Mode { MOVE, ROTATE, SCALE, GEOMETRY }
    private enum Axis { X, Y, Z, NONE }
    private enum Operation { MOVE_AXIS, MOVE_FREE, ROTATE, SCALE_AXIS, RADIUS, HEIGHT, DIMENSION, POINT }

    private record Handle(Operation operation, Axis axis, int point, ObjModelShape shape, Color color) {}

    private final String session = UUID.randomUUID().toString();
    private final List<Handle> handles = new ArrayList<>();
    private ShapeKeyframe keyframe;
    private Mode mode = Mode.MOVE;
    private String layoutKey = "";
    private Handle hovered;
    private Handle dragging;
    private ShapeState dragStart;
    private Vec3 dragAxis;
    private Vec3 dragOrigin;
    private Vec3 dragPlaneNormal;
    private Vec3 dragPlaneStart;
    private double dragParameter;
    private double dragAngle;
    private long lastSeen;
    private boolean rightMouseDown;

    @Override
    public void edit(ShapeKeyframe keyframe, Consumer<Consumer<ShapeKeyframe>> update) {
        lastSeen = System.nanoTime();
        if (this.keyframe != keyframe) {
            this.keyframe = keyframe;
            dragging = null;
            rebuild(keyframe.state);
        }

        ImGui.text("Viewport Gizmo");
        setModeButton("Move", Mode.MOVE); ImGui.sameLine();
        setModeButton("Rotate", Mode.ROTATE); ImGui.sameLine();
        setModeButton("Scale", Mode.SCALE); ImGui.sameLine();
        setModeButton("Geometry", Mode.GEOMETRY);

        ShapeState state = keyframe.state;
        String wantedLayout = layoutKey(state);
        if (!wantedLayout.equals(layoutKey)) rebuild(state);
        updateHandles(state);

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 mouseDirection = ReplayUI.getMouseLookVector();
        if (mouseDirection == null || ReplayUI.hasAnyPopupOpen) {
            setHovered(null);
            return;
        }

        RayModelIntersection.Ray ray = new RayModelIntersection.Ray(camera.position(), mouseDirection);
        if (dragging == null) {
            setHovered(pick(ray));
            if (hovered != null && ImGui.isMouseClicked(0)) beginDrag(hovered, state, ray, camera);
        } else if (ImGui.isMouseDown(0)) {
            ShapeState replacement = drag(ray, camera);
            if (replacement != null) {
                ShapeTrackRegistry.apply(replacement);
                update.accept(changed -> changed.state = replacement);
            }
        } else {
            dragging = null;
            updateColors();
        }
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean rightDown = minecraft.options.keyUse.isDown();
        if (rightDown && !rightMouseDown && ReplayUI.isActive() && ReplayUI.isMainFrameHovered()
                && !ReplayUI.hasAnyPopupOpen) {
            Vec3 direction = ReplayUI.getMouseLookVector();
            if (direction != null) {
                String shapeId = ShapeTrackRegistry.pickShape(new RayModelIntersection.Ray(
                        minecraft.gameRenderer.mainCamera().position(), direction));
                if (shapeId != null) ShapeTimelineSelection.request(shapeId);
            }
        }
        rightMouseDown = rightDown;
        if (keyframe != null && System.nanoTime() - lastSeen > 500_000_000L) clear();
    }

    public void clear() {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        ShapeManagers.removeShapes(Vector3.id("gizmo/" + session));
        keyframe = null;
        hovered = null;
        dragging = null;
        rightMouseDown = false;
        layoutKey = "";
    }

    private void setModeButton(String label, Mode candidate) {
        if (ImGui.radioButton(label + "##shape_gizmo", mode == candidate) && mode != candidate) {
            mode = candidate;
            dragging = null;
            if (keyframe != null) rebuild(keyframe.state);
        }
    }

    private String layoutKey(ShapeState state) {
        int points = state.points() == null ? 0 : state.points().size();
        return mode + ":" + state.shapeType() + ":" + points;
    }

    private void rebuild(ShapeState state) {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        ShapeManagers.removeShapes(Vector3.id("gizmo/" + session));
        layoutKey = layoutKey(state);
        switch (mode) {
            case MOVE -> {
                add(Operation.MOVE_AXIS, Axis.X, -1, MOVE_MODEL, X_COLOR);
                add(Operation.MOVE_AXIS, Axis.Y, -1, MOVE_MODEL, Y_COLOR);
                add(Operation.MOVE_AXIS, Axis.Z, -1, MOVE_MODEL, Z_COLOR);
                add(Operation.MOVE_FREE, Axis.NONE, -1, CENTER_MODEL, PROPERTY_COLOR);
            }
            case ROTATE -> {
                add(Operation.ROTATE, Axis.X, -1, ROTATE_MODEL, X_COLOR);
                add(Operation.ROTATE, Axis.Y, -1, ROTATE_MODEL, Y_COLOR);
                add(Operation.ROTATE, Axis.Z, -1, ROTATE_MODEL, Z_COLOR);
            }
            case SCALE -> {
                add(Operation.SCALE_AXIS, Axis.X, -1, SCALE_MODEL, X_COLOR);
                add(Operation.SCALE_AXIS, Axis.Y, -1, SCALE_MODEL, Y_COLOR);
                add(Operation.SCALE_AXIS, Axis.Z, -1, SCALE_MODEL, Z_COLOR);
            }
            case GEOMETRY -> addGeometryHandles(state);
        }
    }

    private void addGeometryHandles(ShapeState state) {
        switch (state.shapeType()) {
            case "box", "box_wireframe", "wireframed_box" -> {
                add(Operation.DIMENSION, Axis.X, -1, SCALE_MODEL, X_COLOR);
                add(Operation.DIMENSION, Axis.Y, -1, SCALE_MODEL, Y_COLOR);
                add(Operation.DIMENSION, Axis.Z, -1, SCALE_MODEL, Z_COLOR);
            }
            case "cylinder", "cylinder_wireframe", "cone", "cone_wireframe" -> {
                add(Operation.HEIGHT, Axis.X, -1, SCALE_MODEL, X_COLOR);
                add(Operation.RADIUS, Axis.Y, -1, SCALE_MODEL, PROPERTY_COLOR);
            }
            case "face_circle", "line_circle" -> add(Operation.RADIUS, Axis.Y, -1, SCALE_MODEL, PROPERTY_COLOR);
            case "sphere" -> add(Operation.RADIUS, Axis.X, -1, SCALE_MODEL, PROPERTY_COLOR);
            case "line", "line_strip" -> {
                int count = state.points() == null ? 0 : state.points().size();
                for (int i = 0; i < count; i++) add(Operation.POINT, Axis.NONE, i, CENTER_MODEL, PROPERTY_COLOR);
            }
        }
    }

    private void add(Operation operation, Axis axis, int point, Identifier model, Color color) {
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                model, Vec3.ZERO, color, true);
        Identifier id = Vector3.id("gizmo/" + session + "/" + handles.size());
        ShapeManagers.addShape(id, shape);
        handles.add(new Handle(operation, axis, point, shape, color));
    }

    private void updateHandles(ShapeState state) {
        Vec3 center = center(state);
        double scale = gizmoScale(center);
        for (Handle handle : handles) {
            Vec3 position = handlePosition(state, handle);
            handle.shape().forceSetWorldPosition(position);
            handle.shape().forceSetWorldScale(new Vec3(scale, scale, scale));
            handle.shape().forceSetWorldRotation(handleRotation(state, handle));
        }
        updateColors();
    }

    private Vec3 handlePosition(ShapeState state, Handle handle) {
        Vec3 center = center(state);
        if (handle.operation() == Operation.POINT) return localToWorld(state, state.points().get(handle.point()));
        if (handle.operation() == Operation.DIMENSION) {
            return center.add(localAxis(state, handle.axis()).scale(size(state, handle.axis()) * scale(state, handle.axis()) / 2));
        }
        if (handle.operation() == Operation.HEIGHT) {
            return center.add(localAxis(state, Axis.X).scale(state.sizeY() * state.scaleX() / 2));
        }
        if (handle.operation() == Operation.RADIUS) {
            Axis radial = handle.axis();
            return center.add(localAxis(state, radial).scale(state.sizeX() * scale(state, radial) / 2));
        }
        return center;
    }

    private Vector3f handleRotation(ShapeState state, Handle handle) {
        float x = 0, y = 0, z = 0;
        if (handle.axis() == Axis.X) z = -90;
        if (handle.axis() == Axis.Z) x = 90;
        if (handle.operation() == Operation.ROTATE && handle.axis() == Axis.Z) z = 90;
        if (mode == Mode.GEOMETRY) {
            x += state.pitch(); y += state.yaw(); z += state.roll();
        }
        return new Vector3f(x, y, z);
    }

    private double gizmoScale(Vec3 position) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        double distance = Math.max(0.25, camera.position().distanceTo(position));
        double height = Math.max(1, ReplayUI.viewportSizeY);
        return Math.max(0.015, 70.0 * (2 * distance * Math.tan(Math.toRadians(camera.getFov()) / 2) / height));
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
            handle.shape().setBaseColor(handle == dragging ? ACTIVE_COLOR
                    : handle == hovered ? HOVER_COLOR : handle.color());
        }
    }

    private void beginDrag(Handle handle, ShapeState state, RayModelIntersection.Ray ray, Camera camera) {
        dragging = handle;
        dragStart = state;
        dragOrigin = handle.operation() == Operation.POINT ? handlePosition(state, handle) : center(state);
        dragAxis = handle.axis() == Axis.NONE ? Vec3.ZERO
                : mode == Mode.GEOMETRY ? localAxis(state, handle.axis()) : axis(handle.axis());
        dragPlaneNormal = new Vec3(camera.forwardVector());
        if (handle.operation() == Operation.MOVE_FREE || handle.operation() == Operation.POINT) {
            dragPlaneStart = intersectPlane(ray, dragOrigin, dragPlaneNormal);
        } else if (handle.operation() == Operation.ROTATE) {
            Vec3 point = intersectPlane(ray, dragOrigin, dragAxis);
            dragAngle = point == null ? 0 : angleOnPlane(point.subtract(dragOrigin), dragAxis);
        } else {
            dragParameter = axisParameter(ray, dragOrigin, dragAxis);
        }
        updateColors();
    }

    private ShapeState drag(RayModelIntersection.Ray ray, Camera camera) {
        if (dragging.operation() == Operation.MOVE_FREE || dragging.operation() == Operation.POINT) {
            Vec3 current = intersectPlane(ray, dragOrigin, dragPlaneNormal);
            if (current == null || dragPlaneStart == null) return null;
            Vec3 delta = current.subtract(dragPlaneStart);
            if (dragging.operation() == Operation.MOVE_FREE) return withPosition(dragStart, center(dragStart).add(delta));
            List<ShapePoint> points = new ArrayList<>(dragStart.points());
            Vec3 localDelta = worldDeltaToLocal(dragStart, delta);
            ShapePoint point = points.get(dragging.point());
            points.set(dragging.point(), new ShapePoint(point.x() + localDelta.x,
                    point.y() + localDelta.y, point.z() + localDelta.z));
            return with(dragStart, null, null, null, null, points);
        }
        if (dragging.operation() == Operation.ROTATE) {
            Vec3 point = intersectPlane(ray, dragOrigin, dragAxis);
            if (point == null) return null;
            float delta = (float) Math.toDegrees(wrapAngle(angleOnPlane(point.subtract(dragOrigin), dragAxis) - dragAngle));
            float[] rotation = {dragStart.pitch(), dragStart.yaw(), dragStart.roll()};
            rotation[index(dragging.axis())] += delta;
            return with(dragStart, null, rotation, null, null, null);
        }

        double delta = axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
        return switch (dragging.operation()) {
            case MOVE_AXIS -> withPosition(dragStart, center(dragStart).add(dragAxis.scale(delta)));
            case SCALE_AXIS -> {
                float[] scale = {(float) dragStart.scaleX(), (float) dragStart.scaleY(), (float) dragStart.scaleZ()};
                int index = index(dragging.axis());
                scale[index] = Math.max(0.001f, scale[index] + (float) delta);
                yield with(dragStart, null, null, scale, null, null);
            }
            case DIMENSION -> withDimensionDelta(dragStart, dragging.axis(), delta);
            case HEIGHT -> withHeightDelta(dragStart, delta);
            case RADIUS -> withRadiusDelta(dragStart, dragging.axis(), delta);
            default -> null;
        };
    }

    private static ShapeState withDimensionDelta(ShapeState state, Axis axis, double worldDelta) {
        float[] size = {(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        int index = index(axis);
        size[index] = Math.max(0.001f, size[index] + (float) (2 * worldDelta / scale(state, axis)));
        return with(state, null, null, null, size, null);
    }

    private static ShapeState withHeightDelta(ShapeState state, double worldDelta) {
        float[] size = {(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        size[1] = Math.max(0.001f, size[1] + (float) (2 * worldDelta / state.scaleX()));
        return with(state, null, null, null, size, null);
    }

    private static ShapeState withRadiusDelta(ShapeState state, Axis axis, double worldDelta) {
        float[] size = {(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        size[0] = Math.max(0.001f, size[0] + (float) (2 * worldDelta / scale(state, axis)));
        return with(state, null, null, null, size, null);
    }

    private static ShapeState withPosition(ShapeState state, Vec3 position) {
        return with(state, new float[]{(float) position.x, (float) position.y, (float) position.z},
                null, null, null, null);
    }

    private static ShapeState with(ShapeState state, float[] position, float[] rotation,
            float[] scale, float[] size, List<ShapePoint> points) {
        if (position == null) position = new float[]{(float) state.x(), (float) state.y(), (float) state.z()};
        if (rotation == null) rotation = new float[]{state.pitch(), state.yaw(), state.roll()};
        if (scale == null) scale = new float[]{(float) state.scaleX(), (float) state.scaleY(), (float) state.scaleZ()};
        if (size == null) size = new float[]{(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        if (points == null) points = state.points();
        return state.with(position, rotation, scale, size, state.segments(), state.lineWidth(), state.color(),
                points, state.text(), state.parentShapeId(), state.seeThrough(), state.visible());
    }

    private static Vec3 center(ShapeState state) { return new Vec3(state.x(), state.y(), state.z()); }

    private static double size(ShapeState state, Axis axis) {
        return switch (axis) { case X -> state.sizeX(); case Y -> state.sizeY(); case Z -> state.sizeZ(); default -> 0; };
    }

    private static double scale(ShapeState state, Axis axis) {
        return switch (axis) { case X -> state.scaleX(); case Y -> state.scaleY(); case Z -> state.scaleZ(); default -> 1; };
    }

    private static int index(Axis axis) {
        return switch (axis) { case X -> 0; case Y -> 1; case Z -> 2; default -> -1; };
    }

    private static Vec3 axis(Axis axis) {
        return switch (axis) { case X -> new Vec3(1, 0, 0); case Y -> new Vec3(0, 1, 0); case Z -> new Vec3(0, 0, 1); default -> Vec3.ZERO; };
    }

    private static Quaternionf rotation(ShapeState state) {
        return new Quaternionf().rotateXYZ((float) Math.toRadians(state.pitch()),
                (float) Math.toRadians(state.yaw()), (float) Math.toRadians(state.roll()));
    }

    private static Vec3 localAxis(ShapeState state, Axis axis) {
        Vector3f value = axis(axis).toVector3f().rotate(rotation(state));
        return new Vec3(value.x, value.y, value.z).normalize();
    }

    private static Vec3 localToWorld(ShapeState state, ShapePoint point) {
        Vector3f value = new Vector3f((float) (point.x() * state.scaleX()),
                (float) (point.y() * state.scaleY()), (float) (point.z() * state.scaleZ())).rotate(rotation(state));
        return center(state).add(value.x, value.y, value.z);
    }

    private static Vec3 worldDeltaToLocal(ShapeState state, Vec3 delta) {
        Vector3f value = delta.toVector3f().rotate(rotation(state).invert());
        return new Vec3(value.x / state.scaleX(), value.y / state.scaleY(), value.z / state.scaleZ());
    }

    private static double axisParameter(RayModelIntersection.Ray ray, Vec3 origin, Vec3 axis) {
        Vec3 direction = ray.direction.normalize();
        double dot = axis.dot(direction);
        double denominator = 1 - dot * dot;
        if (Math.abs(denominator) < 1.0e-5) return 0;
        Vec3 offset = origin.subtract(ray.origin);
        return (dot * direction.dot(offset) - axis.dot(offset)) / denominator;
    }

    private static Vec3 intersectPlane(RayModelIntersection.Ray ray, Vec3 point, Vec3 normal) {
        double denominator = ray.direction.dot(normal);
        if (Math.abs(denominator) < 1.0e-6) return null;
        double distance = point.subtract(ray.origin).dot(normal) / denominator;
        return ray.origin.add(ray.direction.scale(distance));
    }

    private static double angleOnPlane(Vec3 value, Vec3 normal) {
        Vec3 reference = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 first = normal.cross(reference).normalize();
        Vec3 second = normal.cross(first).normalize();
        return Math.atan2(value.dot(second), value.dot(first));
    }

    private static double wrapAngle(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2;
        while (angle < -Math.PI) angle += Math.PI * 2;
        return angle;
    }
}
