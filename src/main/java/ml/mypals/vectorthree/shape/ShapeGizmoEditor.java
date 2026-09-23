package ml.mypals.vectorthree.shape;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.box.BoxWireframeShape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final double ARROW_POINT_GIZMO_SCALE = 0.1;
    private static final Color AABB_COLOR = new Color(255, 220, 0, 255);
    private static final Color CENTER_POINT_COLOR = new Color(255, 40, 40, 255);
    private static final Color AREA_SELECTION_COLOR = new Color(60, 140, 255, 255);
    private static final float AABB_EDGE_WIDTH = 1F;
    private static final double CENTER_POINT_GIZMO_SCALE = 0.25;

    private enum Mode { MOVE, ROTATE, SCALE, GEOMETRY }
    private enum Axis { X, Y, Z, NONE }
    private enum Operation { MOVE_AXIS, MOVE_FREE, ROTATE, SCALE_AXIS, SCALE_UNIFORM, RADIUS, HEIGHT, DIMENSION, POINT }

    private record Handle(Operation operation, Axis axis, int point, ObjModelShape shape, Color color) {}

    private final String session = UUID.randomUUID().toString();
    private final List<Handle> handles = new ArrayList<>();
    private ShapeKeyframe keyframe;
    private Consumer<ShapeState> commit = state -> {};
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
    private ShapeState previewState;
    private final Set<Integer> heldShortcutKeys = new HashSet<>();
    private BoxWireframeShape aabbBox;
    private ObjModelShape centerPoint;
    private BoxWireframeShape areaSelectionBox;

    @Override
    public void edit(ShapeKeyframe keyframe, Consumer<Consumer<ShapeKeyframe>> update) {
        select(keyframe, replacement -> update.accept(changed -> changed.state = replacement));

        ImGui.text(I18n.get("vector3.gizmo.viewport_gizmo"));
        setModeButton(I18n.get("vector3.gizmo.move"), Mode.MOVE); ImGui.sameLine();
        setModeButton(I18n.get("vector3.gizmo.rotate"), Mode.ROTATE); ImGui.sameLine();
        setModeButton(I18n.get("vector3.gizmo.scale"), Mode.SCALE); ImGui.sameLine();
        setModeButton(I18n.get("vector3.gizmo.geometry"), Mode.GEOMETRY);

    }

    public void frame() {
        if (!ReplayUI.isActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        handleShortcuts();
        if (dragging != null && ReplayUI.imguiWindower.isGrabbed()) {
            ReplayUI.imguiWindower.ungrab();
        }

        if (dragging != null && !ImGui.isMouseDown(1)) {
            if (previewState != null) commit.accept(previewState);
            previewState = null;
            dragging = null;
            updateColors();
            return;
        }

        boolean inViewport = mouseInViewport();
        Vec3 direction = ReplayUI.getMouseLookVector();
        if (direction == null && dragging != null) direction = unboundedMouseLookVector();
        if (direction == null) return;
        Camera camera = minecraft.gameRenderer.mainCamera();
        RayModelIntersection.Ray ray = new RayModelIntersection.Ray(camera.position(), direction);

        ShapeState state = null;
        if (keyframe != null) {
            state = previewState == null ? keyframe.state : previewState;
            String wantedLayout = layoutKey(state);
            if (!wantedLayout.equals(layoutKey)) rebuild(state);
            updateHandles(state);
            updateAabbMarker(state);
            updateAreaSelectionMarker(state);
        }

        if (dragging == null) {
            setHovered(keyframe != null && inViewport ? pick(ray) : null);
            if (ImGui.isMouseClicked(1) && hovered != null) {
                ReplayUI.imguiWindower.ungrab();
                beginDrag(hovered, state, ray, camera);
            } else if (ImGui.isMouseClicked(1) && inViewport) {
                String shapeId = ShapeTrackRegistry.pickShape(ray);
                if (shapeId != null) ShapeTimelineSelection.request(shapeId);
            }
        }

        if (dragging != null && ImGui.isMouseDown(1)) {
            ShapeState replacement = drag(ray, camera);
            if (replacement != null) {
                previewState = replacement;
                ShapeTrackRegistry.apply(replacement);
                updateHandles(replacement);
                updateAabbMarker(replacement);
                updateAreaSelectionMarker(replacement);
            }
        }
    }

    public boolean isDragging() {
        return dragging != null;
    }

    public void select(ShapeKeyframe keyframe, Consumer<ShapeState> commit) {
        this.commit = commit;
        if (this.keyframe == keyframe) return;
        this.keyframe = keyframe;
        previewState = null;
        dragging = null;
        rebuild(keyframe.state);
        updateHandles(keyframe.state);
        updateAabbMarker(keyframe.state);
        updateAreaSelectionMarker(keyframe.state);
    }

    public void clearSelection() {
        if (isDragging()) return;
        if (keyframe != null) clear();
    }

    public void clear() {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        ShapeManagers.removeShapes(Vector3.id("gizmo/" + session));
        removeAabbMarker();
        removeAreaSelectionMarker();
        keyframe = null;
        hovered = null;
        dragging = null;
        previewState = null;
        commit = state -> {};
        layoutKey = "";
    }

    private void setModeButton(String label, Mode candidate) {
        if (ImGui.radioButton(label + "##shape_gizmo", mode == candidate) && mode != candidate) {
            setMode(candidate);
        }
    }

    private void handleShortcuts() {
        if (keyframe == null || dragging != null || ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive()) return;
        boolean move = keyJustPressed(InputConstants.KEY_G);
        boolean scale = keyJustPressed(InputConstants.KEY_B);
        boolean rotate = keyJustPressed(InputConstants.KEY_R);
        boolean geometry = keyJustPressed(InputConstants.KEY_M);
        Mode requested = move ? Mode.MOVE : scale ? Mode.SCALE : rotate ? Mode.ROTATE : geometry ? Mode.GEOMETRY : null;
        if (requested != null) setMode(requested);
    }

    private boolean keyJustPressed(int key) {
        boolean down = InputConstants.isKeyDown(key);
        boolean wasDown = down ? !heldShortcutKeys.add(key) : heldShortcutKeys.remove(key);
        return down && !wasDown;
    }

    private void setMode(Mode requested) {
        if (mode == requested) return;
        mode = requested;
        dragging = null;
        if (keyframe != null) {
            rebuild(keyframe.state);
            updateHandles(keyframe.state);
            updateAabbMarker(keyframe.state);
            updateAreaSelectionMarker(keyframe.state);
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
                add(Operation.SCALE_UNIFORM, Axis.NONE, -1, CENTER_MODEL, PROPERTY_COLOR);
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
                add(Operation.HEIGHT, Axis.Y, -1, MOVE_MODEL, Y_COLOR);
                add(Operation.RADIUS, Axis.X, -1, MOVE_MODEL, PROPERTY_COLOR);
            }
            case "face_circle", "line_circle" -> add(Operation.RADIUS, Axis.Y, -1, SCALE_MODEL, PROPERTY_COLOR);
            case "sphere" -> add(Operation.RADIUS, Axis.X, -1, SCALE_MODEL, PROPERTY_COLOR);
            case "line", "line_strip", "arrow", "area" -> {
                int count = state.points() == null ? 0 : state.points().size();
                for (int i = 0; i < count; i++) {
                    add(Operation.POINT, Axis.X, i, MOVE_MODEL, X_COLOR);
                    add(Operation.POINT, Axis.Y, i, MOVE_MODEL, Y_COLOR);
                    add(Operation.POINT, Axis.Z, i, MOVE_MODEL, Z_COLOR);
                    add(Operation.POINT, Axis.NONE, i, CENTER_MODEL, PROPERTY_COLOR);
                }
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
            double handleScale = scale;
            if (handle.operation() == Operation.POINT && state.shapeType().equals("arrow"))
                handleScale *= ARROW_POINT_GIZMO_SCALE;
            if (handle.operation() == Operation.POINT && handle.axis() == Axis.NONE
                    || handle.operation() == Operation.SCALE_UNIFORM) handleScale *= 0.5;
            handle.shape().forceSetWorldPosition(position);
            handle.shape().forceSetWorldScale(new Vec3(handleScale, handleScale, handleScale));
            handle.shape().forceSetWorldRotation(handleRotation(state, handle));
        }
        updateColors();
    }

    private void updateAabbMarker(ShapeState state) {
        Shape target = ShapeTrackRegistry.shape(state.shapeId());
        List<Vec3> vertices = target == null ? null : target.getModel(false);
        Vec3 origin = center(state);
        Quaternionf orientation = usesAbsolutePoints(state) ? new Quaternionf() : rotation(state);
        Quaternionf toLocal = new Quaternionf(orientation).invert();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        if (vertices != null && !vertices.isEmpty()) {
            min.set(Float.POSITIVE_INFINITY);
            max.set(Float.NEGATIVE_INFINITY);
            for (Vec3 vertex : vertices) {
                Vector3f local = vertex.subtract(origin).toVector3f().rotate(toLocal);
                min.min(local);
                max.max(local);
            }
        }
        Vector3f localCenter = new Vector3f(min).add(max).mul(0.5f);
        Vector3f worldOffset = new Vector3f(localCenter).rotate(orientation);
        Vec3 markerCenter = origin.add(worldOffset.x, worldOffset.y, worldOffset.z);
        Vec3 halfSize = new Vec3(new Vector3f(max).sub(min).mul(0.5f));

        ensureAabbMarker();
        aabbBox.forceSetCorners(markerCenter.subtract(halfSize), markerCenter.add(halfSize));
        Vector3f euler = orientation.getEulerAnglesXYZ(new Vector3f());
        aabbBox.forceSetWorldRotation(new Vector3f((float) Math.toDegrees(euler.x),
                (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z)));
        double scale = gizmoScale(markerCenter) * CENTER_POINT_GIZMO_SCALE;
        centerPoint.forceSetWorldPosition(markerCenter);
        centerPoint.forceSetWorldScale(new Vec3(scale, scale, scale));
    }

    private void ensureAabbMarker() {
        if (aabbBox != null) return;
        aabbBox = ShapeGenerator.generateBoxWireframe()
                .aabb(Vec3.ZERO, new Vec3(1, 1, 1))
                .edgeWidth(AABB_EDGE_WIDTH)
                .color(AABB_COLOR)
                .seeThrough(true)
                .build(Shape.RenderingType.BATCH);
        ShapeManagers.addShape(Vector3.id("gizmo_aabb/" + session), aabbBox);
        centerPoint = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                CENTER_MODEL, Vec3.ZERO, CENTER_POINT_COLOR, true);
        ShapeManagers.addShape(Vector3.id("gizmo_center/" + session), centerPoint);
    }

    private void removeAabbMarker() {
        if (aabbBox == null) return;
        aabbBox.discard();
        centerPoint.discard();
        ShapeManagers.removeShapes(Vector3.id("gizmo_aabb/" + session));
        ShapeManagers.removeShapes(Vector3.id("gizmo_center/" + session));
        aabbBox = null;
        centerPoint = null;
    }

    private void updateAreaSelectionMarker(ShapeState state) {
        if (!state.shapeType().equals("area") || state.points() == null || state.points().size() < 2) {
            removeAreaSelectionMarker();
            return;
        }
        Vec3 a = state.points().get(0).vec3();
        Vec3 b = state.points().get(1).vec3();
         double inset = 0.01;
        Vec3 min = new Vec3(Math.min(a.x, b.x) - inset, Math.min(a.y, b.y) - inset, Math.min(a.z, b.z) - inset);
        Vec3 max = new Vec3(Math.max(a.x, b.x) + inset, Math.max(a.y, b.y) + inset, Math.max(a.z, b.z) + inset);
        ensureAreaSelectionMarker();
        areaSelectionBox.forceSetCorners(min, max);
    }

    private void ensureAreaSelectionMarker() {
        if (areaSelectionBox != null) return;
        areaSelectionBox = ShapeGenerator.generateBoxWireframe()
                .aabb(Vec3.ZERO, new Vec3(1, 1, 1))
                .edgeWidth(AABB_EDGE_WIDTH)
                .color(AREA_SELECTION_COLOR)
                .seeThrough(true)
                .build(Shape.RenderingType.BATCH);
        ShapeManagers.addShape(Vector3.id("gizmo_area_selection/" + session), areaSelectionBox);
    }

    private void removeAreaSelectionMarker() {
        if (areaSelectionBox == null) return;
        areaSelectionBox.discard();
        ShapeManagers.removeShapes(Vector3.id("gizmo_area_selection/" + session));
        areaSelectionBox = null;
    }

    private static boolean usesAbsolutePoints(ShapeState state) {
        return state.shapeType().equals("area");
    }

    private Vec3 handlePosition(ShapeState state, Handle handle) {
        Vec3 center = center(state);
        if (handle.operation() == Operation.POINT) {
            ShapePoint point = state.points().get(handle.point());
            return usesAbsolutePoints(state) ? point.vec3() : localToWorld(state, point);
        }
        if (handle.operation() == Operation.DIMENSION) {
            return center.add(localAxis(state, handle.axis()).scale(size(state, handle.axis()) * scale(state, handle.axis()) / 2));
        }
        if (handle.operation() == Operation.HEIGHT) {
            return center.add(localAxis(state, handle.axis())
                    .scale(state.sizeY() * scale(state, handle.axis()) / 2));
        }
        if (handle.operation() == Operation.RADIUS) {
            Axis radial = handle.axis();
            return center.add(localAxis(state, radial).scale(state.sizeX() * scale(state, radial) / 2));
        }
        return center;
    }

    private Vector3f handleRotation(ShapeState state, Handle handle) {
        float x = 0, y = 0, z = 0;
        if (handle.operation() == Operation.ROTATE) {
            if (handle.axis() == Axis.Y) z = 90;
            else if (handle.axis() == Axis.Z) y = 90;
        } else {
            if (handle.axis() == Axis.X) z = -90;
            if (handle.axis() == Axis.Z) x = 90;
        }
        if (mode == Mode.GEOMETRY && !usesAbsolutePoints(state)) {
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
        dragAxis = handle.operation() == Operation.SCALE_UNIFORM
                ? new Vec3(camera.leftVector()).scale(-1)
                : handle.axis() == Axis.NONE ? Vec3.ZERO
                : mode == Mode.GEOMETRY && !usesAbsolutePoints(state) ? localAxis(state, handle.axis())
                : axis(handle.axis());
        dragPlaneNormal = new Vec3(camera.forwardVector());
        if (handle.operation() == Operation.MOVE_FREE
                || handle.operation() == Operation.POINT && handle.axis() == Axis.NONE) {
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
        if (dragging.operation() == Operation.MOVE_FREE
                || dragging.operation() == Operation.POINT && dragging.axis() == Axis.NONE) {
            Vec3 current = intersectPlane(ray, dragOrigin, dragPlaneNormal);
            if (current == null || dragPlaneStart == null) return null;
            Vec3 delta = current.subtract(dragPlaneStart);
            if (dragging.operation() == Operation.MOVE_FREE) return withPosition(dragStart, center(dragStart).add(delta));
            List<ShapePoint> points = new ArrayList<>(dragStart.points());
            Vec3 pointDelta = usesAbsolutePoints(dragStart) ? delta : worldDeltaToLocal(dragStart, delta);
            ShapePoint point = points.get(dragging.point());
            points.set(dragging.point(), movedPoint(dragStart, point, pointDelta));
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
            case SCALE_UNIFORM -> {
                float factor = (float) Math.max(0.001,
                        1 + delta / Math.max(0.05, gizmoScale(dragOrigin) * 3));
                float[] scale = {(float) dragStart.scaleX() * factor,
                        (float) dragStart.scaleY() * factor, (float) dragStart.scaleZ() * factor};
                yield with(dragStart, null, null, scale, null, null);
            }
            case DIMENSION -> withDimensionDelta(dragStart, dragging.axis(), delta);
            case HEIGHT -> withHeightDelta(dragStart, dragging.axis(), delta);
            case RADIUS -> withRadiusDelta(dragStart, dragging.axis(), delta);
            case POINT -> withPointDelta(dragStart, dragging.point(), dragAxis.scale(delta));
            default -> null;
        };
    }

    private static ShapeState withPointDelta(ShapeState state, int pointIndex, Vec3 worldDelta) {
        List<ShapePoint> points = new ArrayList<>(state.points());
        Vec3 pointDelta = usesAbsolutePoints(state) ? worldDelta : worldDeltaToLocal(state, worldDelta);
        ShapePoint point = points.get(pointIndex);
        points.set(pointIndex, movedPoint(state, point, pointDelta));
        return with(state, null, null, null, null, points);
    }

    private static ShapePoint movedPoint(ShapeState state, ShapePoint point, Vec3 delta) {
        double x = point.x() + delta.x, y = point.y() + delta.y, z = point.z() + delta.z;
        return usesAbsolutePoints(state)
                ? new ShapePoint(Math.round(x), Math.round(y), Math.round(z))
                : new ShapePoint(x, y, z);
    }

    private static ShapeState withDimensionDelta(ShapeState state, Axis axis, double worldDelta) {
        float[] size = {(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        int index = index(axis);
        size[index] = Math.max(0.001f, size[index] + (float) (2 * worldDelta / scale(state, axis)));
        return with(state, null, null, null, size, null);
    }

    private static ShapeState withHeightDelta(ShapeState state, Axis axis, double worldDelta) {
        float[] size = {(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        size[1] = Math.max(0.001f, size[1] + (float) (2 * worldDelta / scale(state, axis)));
        return with(state, null, null, null, size, null);
    }

    private static ShapeState withRadiusDelta(ShapeState state, Axis axis, double worldDelta) {
        float[] size = {(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        size[0] = Math.max(0.001f, size[0] + (float) (2 * worldDelta / scale(state, axis)));
        return with(state, null, null, null, size, null);
    }

    /** {@code worldPosition} is where the shape should appear; converted to parent-local before storing. */
    private static ShapeState withPosition(ShapeState state, Vec3 worldPosition) {
        Vector3f local = parentWorldTransform(state).invert().transformPosition(worldPosition.toVector3f());
        return with(state, new float[]{local.x, local.y, local.z}, null, null, null, null);
    }

    private static ShapeState with(ShapeState state, float[] position, float[] rotation,
            float[] scale, float[] size, List<ShapePoint> points) {
        if (position == null) position = new float[]{(float) state.x(), (float) state.y(), (float) state.z()};
        if (rotation == null) rotation = new float[]{state.pitch(), state.yaw(), state.roll()};
        if (scale == null) scale = new float[]{(float) state.scaleX(), (float) state.scaleY(), (float) state.scaleZ()};
        if (size == null) size = new float[]{(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        if (points == null) points = state.points();
        return state.with(position, rotation, scale, size, state.segments(), state.lineWidth(), state.color(),
                points, state.text(), state.parentShapeId(), state.seeThrough(), state.visible(),
                state.outline(), state.outlineColor(), state.playAudio(),
                state.manualPlayback(), state.noLoop(), state.playbackSeconds());
    }

    /** state.x/y/z is parent-local (Shape.forceSetWorldPosition combined with the ancestor-chain walk
     *  at draw time), so the gizmo has to walk the same parent chain to know where to actually draw. */
    private static Matrix4f parentWorldTransform(ShapeState state) {
        return ShapeTrackRegistry.worldTransformOrIdentity(state.parentShapeId());
    }

    private static Vec3 center(ShapeState state) {
        Vector3f world = parentWorldTransform(state)
                .transformPosition(new Vector3f((float) state.x(), (float) state.y(), (float) state.z()));
        return new Vec3(world.x, world.y, world.z);
    }

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
        Quaternionf own = new Quaternionf().rotateXYZ((float) Math.toRadians(state.pitch()),
                (float) Math.toRadians(state.yaw()), (float) Math.toRadians(state.roll()));
        return parentWorldTransform(state).getNormalizedRotation(new Quaternionf()).mul(own);
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

    /** Inside the viewport rect and not over an ImGui window drawn on top of it (see EditorCameraController). */
    private static boolean mouseInViewport() {
        var mouse = ReplayUI.getMouseViewportFraction();
        return ReplayUI.isActive() && mouse != null
                && mouse.x >= 0 && mouse.x <= 1 && mouse.y >= 0 && mouse.y <= 1
                && ReplayUI.imguiWindower.getMouseHandledBy().allowGame();
    }

    private static Vec3 unboundedMouseLookVector() {
        if (ReplayUI.lastProjectionMatrix == null || ReplayUI.lastViewQuaternion == null) return null;
        var mouse = ReplayUI.getMouseViewportFraction();
        if (mouse == null) return null;
        Vector4f projected = new Vector4f(mouse.x * 2 - 1, mouse.y * 2 - 1, 0, 1)
                .mul(new Matrix4f(ReplayUI.lastProjectionMatrix).invert());
        return ReplayUI.getMouseLookVectorFromForwards(
                new Vec3(projected.x, -projected.y, projected.z).normalize());
    }
}
