package ml.mypals.vectorthree.fb.shape;

import ml.mypals.vectorthree.fb.editor.EditorInput;
import ml.mypals.vectorthree.core.shape.ShapeState;
import ml.mypals.vectorthree.core.shape.ShapeTimelineSelection;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;

import ml.mypals.vectorthree.fb.Editors;
import ml.mypals.vectorthree.core.Mod;
import java.util.function.UnaryOperator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import ml.mypals.vectorthree.fb.multiedit.GroupTransform;
import ml.mypals.vectorthree.fb.camera.ViewportPick;
import ml.mypals.vectorthree.mc.render.ScreenLayer;
import ml.mypals.vectorthree.mc.shape.area.BlastShape;
import ml.mypals.vectorthree.mixin.flashback.ReplayUIAccessor;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.box.BoxWireframeShape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.core.shape.point.ShapePoint;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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
    public static final Identifier MOVE_MODEL = Mod.id("models/obj/move.obj");
    public static final Identifier ROTATE_MODEL = Mod.id("models/obj/rotation.obj");
    public static final Identifier SCALE_MODEL = Mod.id("models/obj/scale.obj");
    public static final Identifier CENTER_MODEL = Mod.id("models/obj/m_center.obj");
    private static final Color X_COLOR = new Color(255, 55, 55, 230);
    private static final Color Y_COLOR = new Color(55, 255, 55, 230);
    private static final Color Z_COLOR = new Color(70, 100, 255, 230);
    private static final Color PROPERTY_COLOR = new Color(255, 155, 35, 240);
    public static final Color HOVER_COLOR = new Color(255, 235, 40, 255);
    public static final Color ACTIVE_COLOR = Color.WHITE;
    private static final double ARROW_POINT_GIZMO_SCALE = 0.1;
    private static final Color AABB_COLOR = new Color(255, 220, 0, 255);
    private static final Color CENTER_POINT_COLOR = new Color(255, 40, 40, 255);
    private static final Color AREA_SELECTION_COLOR = new Color(60, 140, 255, 255);
    private static final float AABB_EDGE_WIDTH = 1F;
    private static final double CENTER_POINT_GIZMO_SCALE = 0.25;
    public static final double GRID_STEP = 0.5;
    private static final double SCREEN_GRID_STEP = 10;
    public static final double ANGLE_STEP = 15;

    private enum Axis { X, Y, Z, NONE }
    private enum Operation { MOVE_AXIS, MOVE_FREE, ROTATE, SCALE_AXIS, SCALE_UNIFORM, RADIUS, HEIGHT, DIMENSION, POINT }

    private record Handle(Operation operation, Axis axis, int point, ObjModelShape shape, Color color) {}

    private final String session = UUID.randomUUID().toString();
    private final List<Handle> handles = new ArrayList<>();
    private ShapeKeyframe keyframe;
    private Consumer<ShapeState> commit = state -> {};
    private GizmoMode mode = GizmoMode.MOVE;
    private boolean localSpace;
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
    private BoxWireframeShape aabbBox;
    private ObjModelShape centerPoint;
    private BoxWireframeShape areaSelectionBox;
    private List<GroupTransform.Member> group = List.of();
    private Consumer<Map<GroupTransform.Member, ShapeState>> groupCommit = states -> {};
    private Map<GroupTransform.Member, ShapeState> groupStart;
    private Map<GroupTransform.Member, ShapeState> groupPreview;
    private final List<BoxWireframeShape> groupMarkers = new ArrayList<>();

    @Override
    public void edit(ShapeKeyframe keyframe, Consumer<Consumer<ShapeKeyframe>> update) {
        select(keyframe, replacement -> update.accept(changed -> changed.value = replacement));
        controls();
    }

    public void controls() {
        ImGui.text(I18n.get("vector3.gizmo.viewport_gizmo"));
        setModeButton(I18n.get("vector3.gizmo.move"), GizmoMode.MOVE); ImGui.sameLine();
        setModeButton(I18n.get("vector3.gizmo.rotate"), GizmoMode.ROTATE); ImGui.sameLine();
        setModeButton(I18n.get("vector3.gizmo.scale"), GizmoMode.SCALE); ImGui.sameLine();
        setModeButton(I18n.get("vector3.gizmo.geometry"), GizmoMode.GEOMETRY);
        setSpaceButton(I18n.get("vector3.gizmo.global"), false); ImGui.sameLine();
        setSpaceButton(I18n.get("vector3.gizmo.local"), true);
        ImGui.textDisabled(I18n.get("vector3.gizmo.place_hint"));

    }

    public void frame() {
        if (!ReplayUI.isActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        followMode();
        if (dragging != null && EditorInput.isGrabbed()) {
            EditorInput.ungrab();
        }

        if (dragging != null && !ImGui.isMouseDown(1)) {
            if (groupPreview != null) groupCommit.accept(groupPreview);
            else if (previewState != null) commit.accept(previewState);
            groupPreview = null;
            previewState = null;
            dragging = null;
            updateColors();
            return;
        }

        boolean inViewport = mouseInViewport();
        typedValue(inViewport);
        Camera camera = minecraft.gameRenderer.mainCamera();
        RayModelIntersection.Ray ray;
        if (isScreenSpace()) {
            ray = ScreenLayer.mouseRay();
            if (ray == null) return;
        } else {
            Vec3 direction = ReplayUI.getMouseLookVector();
            if (direction == null && dragging != null) direction = unboundedMouseLookVector();
            if (direction == null) return;
            ray = new RayModelIntersection.Ray(camera.position(), direction);
        }

        ShapeState state = null;
        if (keyframe != null) {
            state = previewState == null ? keyframe.value : previewState;
            String wantedLayout = layoutKey(state);
            if (!wantedLayout.equals(layoutKey)) rebuild(state);
            updateHandles(state);
            updateAabbMarker(state);
            updateAreaSelectionMarker(state);
            updateGroupMarkers();
        }

        if (dragging == null) {
            setHovered(keyframe != null && inViewport ? pick(ray) : null);
            if (hovered != null && hovered.operation() == Operation.POINT && state != null && state.shapeType().equals("blast")) {
                ImGui.setTooltip(BlastShape.pointLabel(state, hovered.point()));
            }
            if (ImGui.isMouseClicked(1) && hovered != null) {
                EditorInput.ungrab();
                beginDrag(hovered, state, ray, camera);
            } else if (ImGui.isMouseClicked(1) && inViewport && !Editors.ORBIT_GIZMO.isHovering()
                    && !Editors.CAMERA_GIZMO.isHovering() && !Editors.POSE_GIZMO.isHovering()
                    && !Editors.LIGHT_GIZMO.isHovering() && !Editors.PREFABS.isHovering()
                    && !ml.mypals.vectorthree.fb.camera.MotionPaths.isHovering()) {
                String shapeId = ShapeTrackRegistry.pickShape(ray);
                if (shapeId != null) ShapeTimelineSelection.request(shapeId);
            }
        }

        if (dragging == null && keyframe != null && inViewport && ImGui.isMouseClicked(2)) {
            Vec3 target = isScreenSpace() ? new Vec3(ray.origin.x, ray.origin.y, state.z()) : placementTarget(ray);
            if (target != null && grouped()) {
                Vec3 delta = target.subtract(groupPivot());
                Map<GroupTransform.Member, ShapeState> placed = transformGroup(currentGroupStates(),
                        start -> GroupTransform.move(start, delta));
                previewGroup(placed);
                groupCommit.accept(placed);
            } else if (target != null) {
                ShapeState moved = withPosition(state, target);
                ShapeTrackRegistry.apply(moved);
                commit.accept(moved);
                updateHandles(moved);
                updateAabbMarker(moved);
                updateAreaSelectionMarker(moved);
            }
        }

        if (dragging != null && ImGui.isMouseDown(1) && grouped() && mode != GizmoMode.GEOMETRY) {
            Map<GroupTransform.Member, ShapeState> replacement = dragGroup(ray);
            if (replacement != null) {
                groupPreview = replacement;
                previewState = replacement.get(group.getFirst());
                previewGroup(replacement);
                if (previewState != null) {
                    updateHandles(previewState);
                    updateAabbMarker(previewState);
                    updateAreaSelectionMarker(previewState);
                }
                updateGroupMarkers();
            }
        } else if (dragging != null && ImGui.isMouseDown(1)) {
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

    // Middle click: the clicked surface point, an entity's center, or with Ctrl the cell in front of the clicked face.
    private static Vec3 placementTarget(RayModelIntersection.Ray ray) {
        ViewportPick.Hit hit = ViewportPick.pick(ray.origin, ray.direction);
        if (hit == null) return null;
        if (hit.entity() != null) return hit.entity().getBoundingBox().getCenter();
        if (EditorInput.isCtrlDown()) return Vec3.atCenterOf(hit.block().getBlockPos().relative(hit.block().getDirection()));
        return hit.location();
    }

    public boolean isHovering() {
        return hovered != null || dragging != null;
    }

    public boolean isDragging() {
        return dragging != null;
    }

    public void select(ShapeKeyframe keyframe, Consumer<ShapeState> commit) {
        this.commit = commit;
        if (!group.isEmpty() && !isDragging()) setGroup(List.of());
        if (this.keyframe == keyframe) return;
        this.keyframe = keyframe;
        previewState = null;
        dragging = null;
        rebuild(keyframe.value);
        updateHandles(keyframe.value);
        updateAabbMarker(keyframe.value);
        updateAreaSelectionMarker(keyframe.value);
    }

    /** Several shape keyframes edited together; the first member is the one the handles belong to. */
    public void selectGroup(List<GroupTransform.Member> members, Consumer<Map<GroupTransform.Member, ShapeState>> commit) {
        if (isDragging() || members.isEmpty()) return;
        groupCommit = commit;
        ShapeKeyframe primary = members.getFirst().keyframe();
        if (keyframe != primary) {
            this.commit = state -> {};
            keyframe = primary;
            previewState = null;
            rebuild(primary.value);
        }
        if (!sameMembers(members)) setGroup(members);
        updateHandles(primary.value);
        updateAabbMarker(primary.value);
        updateAreaSelectionMarker(primary.value);
    }

    private boolean sameMembers(List<GroupTransform.Member> members) {
        if (members.size() != group.size()) return false;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).keyframe() != group.get(i).keyframe()) return false;
        }
        return true;
    }

    private void setGroup(List<GroupTransform.Member> members) {
        group = List.copyOf(members);
        groupPreview = null;
        removeGroupMarkers();
    }

    private boolean grouped() {
        return group.size() > 1;
    }

    private Map<GroupTransform.Member, ShapeState> currentGroupStates() {
        Map<GroupTransform.Member, ShapeState> states = new LinkedHashMap<>();
        for (GroupTransform.Member member : group) {
            ShapeState preview = groupPreview == null ? null : groupPreview.get(member);
            states.put(member, preview == null ? member.keyframe().value : preview);
        }
        return states;
    }

    private Set<String> groupShapeIds() {
        Set<String> ids = new HashSet<>();
        for (GroupTransform.Member member : group) ids.add(member.keyframe().value.shapeId());
        return ids;
    }

    private Vec3 groupPivot() {
        Map<GroupTransform.Member, ShapeState> states = currentGroupStates();
        if (localSpace) return center(states.get(group.getFirst()));
        Set<String> ids = groupShapeIds();
        List<ShapeState> movers = states.values().stream().filter(state -> !GroupTransform.followsSelected(state, ids)).toList();
        return GroupTransform.pivot(movers.isEmpty() ? states.values() : movers);
    }

    private Map<GroupTransform.Member, ShapeState> transformGroup(Map<GroupTransform.Member, ShapeState> starts,
            UnaryOperator<ShapeState> transform) {
        Set<String> ids = groupShapeIds();
        Map<GroupTransform.Member, ShapeState> result = new LinkedHashMap<>();
        for (Map.Entry<GroupTransform.Member, ShapeState> entry : starts.entrySet()) {
            ShapeState start = entry.getValue();
            result.put(entry.getKey(), GroupTransform.followsSelected(start, ids) ? start : transform.apply(start));
        }
        return result;
    }

    // One shape can be selected at several ticks; the live preview shows the keyframe nearest the playhead.
    private void previewGroup(Map<GroupTransform.Member, ShapeState> states) {
        int cursor = TimelineWindow.getCursorTick();
        Map<String, GroupTransform.Member> nearest = new HashMap<>();
        for (GroupTransform.Member member : states.keySet()) {
            String id = member.keyframe().value.shapeId();
            GroupTransform.Member best = nearest.get(id);
            if (best == null || Math.abs(member.tick() - cursor) < Math.abs(best.tick() - cursor)) nearest.put(id, member);
        }
        for (GroupTransform.Member member : nearest.values()) ShapeTrackRegistry.apply(states.get(member));
    }

    private Map<GroupTransform.Member, ShapeState> dragGroup(RayModelIntersection.Ray ray) {
        Map<GroupTransform.Member, ShapeState> starts = groupStart;
        if (starts == null) return null;
        boolean snap = EditorInput.isCtrlDown();
        int axis = index(dragging.axis());
        switch (dragging.operation()) {
            case MOVE_FREE -> {
                Vec3 current = intersectPlane(ray, dragOrigin, dragPlaneNormal);
                if (current == null || dragPlaneStart == null) return null;
                Vec3 delta = current.subtract(dragPlaneStart);
                Vec3 moved = snap ? new Vec3(Math.round(delta.x / GRID_STEP) * GRID_STEP,
                        Math.round(delta.y / GRID_STEP) * GRID_STEP, Math.round(delta.z / GRID_STEP) * GRID_STEP) : delta;
                return transformGroup(starts, start -> GroupTransform.move(start, moved));
            }
            case MOVE_AXIS -> {
                double delta = axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
                double distance = snap ? Math.round(delta / GRID_STEP) * GRID_STEP : delta;
                return transformGroup(starts, start -> GroupTransform.move(start, dragAxis.scale(distance)));
            }
            case ROTATE -> {
                Vec3 point = intersectPlane(ray, dragOrigin, dragAxis);
                if (point == null) return null;
                double degrees = Math.toDegrees(wrapAngle(angleOnPlane(point.subtract(dragOrigin), dragAxis) - dragAngle));
                double turned = snap ? Math.round(degrees / ANGLE_STEP) * ANGLE_STEP : degrees;
                return transformGroup(starts, start -> GroupTransform.rotate(start, dragOrigin, dragAxis, axis, turned, localSpace));
            }
            case SCALE_AXIS, SCALE_UNIFORM -> {
                double delta = axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
                double factor = Math.max(0.001, 1 + delta / Math.max(0.05, scaleAt(dragOrigin) * 3));
                double scaled = snap ? Math.max(0.1, Math.round(factor * 10) / 10.0) : factor;
                Vec3 worldAxis = dragging.operation() == Operation.SCALE_AXIS ? dragAxis : null;
                return transformGroup(starts, start -> GroupTransform.scale(start, dragOrigin, worldAxis, axis, scaled, localSpace));
            }
            default -> {
                return null;
            }
        }
    }

    private void updateGroupMarkers() {
        if (!grouped()) {
            removeGroupMarkers();
            return;
        }
        Map<GroupTransform.Member, ShapeState> states = currentGroupStates();
        int index = 0;
        for (GroupTransform.Member member : group) {
            if (member == group.getFirst()) continue;
            if (groupMarkers.size() <= index) {
                BoxWireframeShape marker = ShapeGenerator.generateBoxWireframe()
                        .aabb(Vec3.ZERO, new Vec3(1, 1, 1))
                        .edgeWidth(AABB_EDGE_WIDTH)
                        .color(AABB_COLOR)
                        .seeThrough(true)
                        .build(Shape.RenderingType.BATCH);
                ShapeManagers.addShape(Mod.id("gizmo_group/" + session + "/" + index), marker);
                groupMarkers.add(marker);
            }
            placeAabb(groupMarkers.get(index++), states.get(member));
        }
    }

    private void removeGroupMarkers() {
        for (int i = 0; i < groupMarkers.size(); i++) {
            groupMarkers.get(i).discard();
            ShapeManagers.removeShapes(Mod.id("gizmo_group/" + session + "/" + i));
        }
        groupMarkers.clear();
    }

    public void clearSelection() {
        if (isDragging()) return;
        if (keyframe != null) clear();
    }

    public void clear() {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        ShapeManagers.removeShapes(Mod.id("gizmo/" + session));
        removeAabbMarker();
        removeAreaSelectionMarker();
        removeGroupMarkers();
        group = List.of();
        groupPreview = null;
        groupStart = null;
        keyframe = null;
        hovered = null;
        dragging = null;
        previewState = null;
        commit = state -> {};
        layoutKey = "";
    }

    private void setModeButton(String label, GizmoMode candidate) {
        if (ImGui.radioButton(label + "##shape_gizmo", mode == candidate) && mode != candidate) {
            GizmoMode.set(candidate);
            setMode(candidate);
        }
    }

    private void setSpaceButton(String label, boolean local) {
        if (ImGui.radioButton(label + "##shape_gizmo_space", localSpace == local) && localSpace != local) {
            localSpace = local;
            dragging = null;
            if (keyframe != null) updateHandles(previewState == null ? keyframe.value : previewState);
        }
    }

    // Geometry handles always follow the shape; move, rotate and scale follow it only in local space.
    private boolean usesLocalAxes(ShapeState state) {
        return mode == GizmoMode.GEOMETRY ? !usesAbsolutePoints(state) : localSpace;
    }

    private Vec3 gizmoAxis(ShapeState state, Axis axis) {
        return usesLocalAxes(state) ? localAxis(state, axis) : axis(axis);
    }

    private void followMode() {
        if (dragging == null) setMode(GizmoMode.current());
    }

    private void setMode(GizmoMode requested) {
        if (mode == requested) return;
        mode = requested;
        dragging = null;
        if (keyframe != null) {
            rebuild(keyframe.value);
            updateHandles(keyframe.value);
            updateAabbMarker(keyframe.value);
            updateAreaSelectionMarker(keyframe.value);
        }
    }

    private String layoutKey(ShapeState state) {
        int points = state.points() == null ? 0 : state.points().size();
        String blast = state.shapeType().equals("blast") ? ":" + BlastShape.pointLayout(state) : "";
        return mode + ":" + state.shapeType() + ":" + points + ":" + state.screen() + blast;
    }

    /** The selected shape is on the UI layer: rays, handles and sizes work in its orthographic space. */
    public @org.jetbrains.annotations.Nullable String selectedShapeId() {
        ShapeKeyframe selected = keyframe;
        if (selected == null) return null;
        ShapeState state = previewState != null ? previewState : selected.value;
        return state == null ? null : state.shapeId();
    }

    public boolean isScreenSpace() {
        ShapeKeyframe selected = keyframe;
        if (selected == null) return false;
        ShapeState state = previewState != null ? previewState : selected.value;
        // An area's corners (geometry mode) pick its source region, which stays in the world.
        return state != null && state.screen() && !(usesAbsolutePoints(state) && mode == GizmoMode.GEOMETRY);
    }

    private boolean screenState() {
        ShapeKeyframe selected = keyframe;
        if (selected == null) return false;
        ShapeState state = previewState != null ? previewState : selected.value;
        return state != null && state.screen();
    }

    // The handles follow the gizmo's space; the bounding box marks the shape itself, so it stays on the UI layer,
    // while an area's source selection is in the world.
    public boolean ownsScreenOverlay(Identifier id) {
        if (!screenState() || !id.getNamespace().equals(Mod.ID) || !id.getPath().contains(session)) return false;
        String path = id.getPath();
        if (path.startsWith("gizmo_area_selection")) return false;
        if (path.startsWith("gizmo/")) return isScreenSpace();
        return path.startsWith("gizmo");
    }

    private double scaleAt(Vec3 position) {
        return isScreenSpace() ? ScreenLayer.gizmoScale() : gizmoScale(position);
    }

    private Vec3 viewForward(Camera camera) {
        return isScreenSpace() ? new Vec3(0, 0, -1) : new Vec3(camera.forwardVector());
    }

    private Vec3 viewLeft(Camera camera) {
        return isScreenSpace() ? new Vec3(-1, 0, 0) : new Vec3(camera.leftVector());
    }

    private double gridStep() {
        return isScreenSpace() ? SCREEN_GRID_STEP : GRID_STEP;
    }

    private void rebuild(ShapeState state) {
        for (Handle handle : handles) handle.shape().discard();
        handles.clear();
        ShapeManagers.removeShapes(Mod.id("gizmo/" + session));
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
            case "box" -> {
                add(Operation.DIMENSION, Axis.X, -1, SCALE_MODEL, X_COLOR);
                add(Operation.DIMENSION, Axis.Y, -1, SCALE_MODEL, Y_COLOR);
                add(Operation.DIMENSION, Axis.Z, -1, SCALE_MODEL, Z_COLOR);
            }
            case "cylinder", "cone" -> {
                add(Operation.HEIGHT, Axis.Y, -1, MOVE_MODEL, Y_COLOR);
                add(Operation.RADIUS, Axis.X, -1, MOVE_MODEL, PROPERTY_COLOR);
            }
            case "face_circle" -> add(Operation.RADIUS, Axis.Y, -1, SCALE_MODEL, PROPERTY_COLOR);
            case "sphere" -> add(Operation.RADIUS, Axis.X, -1, SCALE_MODEL, PROPERTY_COLOR);
            case "line", "line_strip", "arrow", "area", "blast" -> {
                int count = state.points() == null ? 0 : state.points().size();
                boolean blast = state.shapeType().equals("blast");
                for (int i = 0; i < count; i++) {
                    String role = blast ? BlastShape.pointRole(state, i) : null;
                    if (blast && role == null) continue;
                    add(Operation.POINT, Axis.X, i, MOVE_MODEL, X_COLOR);
                    add(Operation.POINT, Axis.Y, i, MOVE_MODEL, Y_COLOR);
                    add(Operation.POINT, Axis.Z, i, MOVE_MODEL, Z_COLOR);
                    add(Operation.POINT, Axis.NONE, i, CENTER_MODEL, blast ? BlastShape.pointColor(role) : PROPERTY_COLOR);
                }
            }
        }
    }

    private void add(Operation operation, Axis axis, int point, Identifier model, Color color) {
        // On the UI layer only the in-plane handles make sense: no depth axis, and only the ring about it.
        if (isScreenSpace() && (operation == Operation.ROTATE ? axis != Axis.Z : axis == Axis.Z)) return;
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                model, Vec3.ZERO, color, true);
        Identifier id = Mod.id("gizmo/" + session + "/" + handles.size());
        ShapeManagers.addShape(id, shape);
        handles.add(new Handle(operation, axis, point, shape, color));
    }

    private void updateHandles(ShapeState state) {
        Vec3 center = grouped() && mode != GizmoMode.GEOMETRY ? groupPivot() : center(state);
        double scale = scaleAt(center);
        for (Handle handle : handles) {
            Vec3 position = handlePosition(state, handle);
            // An area's corners sit at its source region, far from its center; each gets its own size.
            double handleScale = mode == GizmoMode.GEOMETRY && usesAbsolutePoints(state) ? scaleAt(position) : scale;
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
        ensureAabbMarker();
        Vec3 markerCenter = placeAabb(aabbBox, state);
        double scale = (screenState() ? ScreenLayer.gizmoScale() : gizmoScale(markerCenter)) * CENTER_POINT_GIZMO_SCALE;
        centerPoint.forceSetWorldPosition(markerCenter);
        centerPoint.forceSetWorldScale(new Vec3(scale, scale, scale));
    }

    private Vec3 placeAabb(BoxWireframeShape box, ShapeState state) {
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

        box.forceSetCorners(markerCenter.subtract(halfSize), markerCenter.add(halfSize));
        Vector3f euler = orientation.getEulerAnglesXYZ(new Vector3f());
        box.forceSetWorldRotation(new Vector3f((float) Math.toDegrees(euler.x),
                (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z)));
        return markerCenter;
    }

    private void ensureAabbMarker() {
        if (aabbBox != null) return;
        aabbBox = ShapeGenerator.generateBoxWireframe()
                .aabb(Vec3.ZERO, new Vec3(1, 1, 1))
                .edgeWidth(AABB_EDGE_WIDTH)
                .color(AABB_COLOR)
                .seeThrough(true)
                .build(Shape.RenderingType.BATCH);
        ShapeManagers.addShape(Mod.id("gizmo_aabb/" + session), aabbBox);
        centerPoint = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                CENTER_MODEL, Vec3.ZERO, CENTER_POINT_COLOR, true);
        ShapeManagers.addShape(Mod.id("gizmo_center/" + session), centerPoint);
    }

    private void removeAabbMarker() {
        if (aabbBox == null) return;
        aabbBox.discard();
        centerPoint.discard();
        ShapeManagers.removeShapes(Mod.id("gizmo_aabb/" + session));
        ShapeManagers.removeShapes(Mod.id("gizmo_center/" + session));
        aabbBox = null;
        centerPoint = null;
    }

    private void updateAreaSelectionMarker(ShapeState state) {
        if (!usesAbsolutePoints(state) || state.points() == null || state.points().size() < 2) {
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
        ShapeManagers.addShape(Mod.id("gizmo_area_selection/" + session), areaSelectionBox);
    }

    private void removeAreaSelectionMarker() {
        if (areaSelectionBox == null) return;
        areaSelectionBox.discard();
        ShapeManagers.removeShapes(Mod.id("gizmo_area_selection/" + session));
        areaSelectionBox = null;
    }

    // Area-like shapes: their first two points are the source region's corners, in world coordinates.
    private static boolean usesAbsolutePoints(ShapeState state) {
        return state.shapeType().equals("area") || state.shapeType().equals("blast");
    }

    // A blast's later points are its path, in the shape's local space.
    private static boolean absolutePoint(ShapeState state, int index) {
        return state.shapeType().equals("area") || state.shapeType().equals("blast") && index < 2;
    }

    private Vec3 handlePosition(ShapeState state, Handle handle) {
        Vec3 center = center(state);
        if (handle.operation() == Operation.POINT) {
            ShapePoint point = state.points().get(handle.point());
            return absolutePoint(state, handle.point()) ? point.vec3() : localToWorld(state, point);
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
        return grouped() && mode != GizmoMode.GEOMETRY ? groupPivot() : center;
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
        if (!usesLocalAxes(state)) return new Vector3f(x, y, z);
        Quaternionf base = new Quaternionf().rotateXYZ((float) Math.toRadians(x), (float) Math.toRadians(y),
                (float) Math.toRadians(z));
        Vector3f euler = rotation(state).mul(base).getEulerAnglesXYZ(new Vector3f());
        return new Vector3f((float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y),
                (float) Math.toDegrees(euler.z));
    }

    public static double gizmoScale(Vec3 position) {
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
        groupStart = grouped() ? currentGroupStates() : null;
        dragOrigin = handle.operation() == Operation.POINT ? handlePosition(state, handle)
                : grouped() && mode != GizmoMode.GEOMETRY ? groupPivot() : center(state);
        dragAxis = handle.operation() == Operation.SCALE_UNIFORM
                ? viewLeft(camera).scale(-1)
                : handle.axis() == Axis.NONE ? Vec3.ZERO
                : gizmoAxis(state, handle.axis());
        dragPlaneNormal = viewForward(camera);
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

    /** Ctrl snaps whatever the drag changed: positions/points and sizes/scale to 0.5, rotation to 15°. */
    private ShapeState drag(RayModelIntersection.Ray ray, Camera camera) {
        ShapeState result = dragUnsnapped(ray);
        return result != null && EditorInput.isCtrlDown() ? snapChanged(result) : result;
    }

    private ShapeState snapChanged(ShapeState state) {
        double step = gridStep();
        float[] position = {(float) snap(state.x(), dragStart.x(), step, false),
                (float) snap(state.y(), dragStart.y(), step, false),
                (float) snap(state.z(), dragStart.z(), step, false)};
        float[] rotation = {state.pitch(), state.yaw(), state.roll()};
        float[] scale = {(float) snap(state.scaleX(), dragStart.scaleX(), step, true),
                (float) snap(state.scaleY(), dragStart.scaleY(), step, true),
                (float) snap(state.scaleZ(), dragStart.scaleZ(), step, true)};
        float[] size = {(float) snap(state.sizeX(), dragStart.sizeX(), GRID_STEP, true),
                (float) snap(state.sizeY(), dragStart.sizeY(), GRID_STEP, true),
                (float) snap(state.sizeZ(), dragStart.sizeZ(), GRID_STEP, true)};
        List<ShapePoint> points = state.points();
        if (points != null && dragStart.points() != null && points.size() == dragStart.points().size()) {
            List<ShapePoint> snapped = new ArrayList<>(points.size());
            for (int i = 0; i < points.size(); i++) {
                ShapePoint now = points.get(i), before = dragStart.points().get(i);
                snapped.add(new ShapePoint(snap(now.x(), before.x(), step, false),
                        snap(now.y(), before.y(), step, false), snap(now.z(), before.z(), step, false)));
            }
            points = snapped;
        }
        return with(state, position, rotation, scale, size, points);
    }

    /** Snaps {@code value} to the step grid only if the drag moved it away from {@code before}. */
    public static double snap(double value, double before, double step, boolean positive) {
        if (Math.abs(value - before) < 1.0e-6) return value;
        double snapped = Math.round(value / step) * step;
        return positive ? Math.max(step, snapped) : snapped;
    }

    private ShapeState dragUnsnapped(RayModelIntersection.Ray ray) {
        if (dragging.operation() == Operation.MOVE_FREE
                || dragging.operation() == Operation.POINT && dragging.axis() == Axis.NONE) {
            Vec3 current = intersectPlane(ray, dragOrigin, dragPlaneNormal);
            if (current == null || dragPlaneStart == null) return null;
            Vec3 delta = current.subtract(dragPlaneStart);
            if (dragging.operation() == Operation.MOVE_FREE) return withPosition(dragStart, center(dragStart).add(delta));
            List<ShapePoint> points = new ArrayList<>(dragStart.points());
            Vec3 pointDelta = absolutePoint(dragStart, dragging.point()) ? delta : worldDeltaToLocal(dragStart, delta);
            ShapePoint point = points.get(dragging.point());
            points.set(dragging.point(), movedPoint(dragStart, dragging.point(), point, pointDelta));
            return with(dragStart, null, null, null, null, points);
        }
        if (dragging.operation() == Operation.ROTATE) {
            Vec3 point = intersectPlane(ray, dragOrigin, dragAxis);
            if (point == null) return null;
            double delta = Math.toDegrees(wrapAngle(angleOnPlane(point.subtract(dragOrigin), dragAxis) - dragAngle));
            if (EditorInput.isCtrlDown()) delta = Math.round(delta / ANGLE_STEP) * ANGLE_STEP;
            return with(dragStart, null, rotatedAbout(dragStart, dragAxis, delta), null, null, null);
        }

        double delta = axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
        return switch (dragging.operation()) {
            case MOVE_AXIS -> withPosition(dragStart, center(dragStart).add(dragAxis.scale(delta)));
            case SCALE_AXIS -> {
                float[] scale = {(float) dragStart.scaleX(), (float) dragStart.scaleY(), (float) dragStart.scaleZ()};
                if (usesLocalAxes(dragStart)) {
                    int index = index(dragging.axis());
                    scale[index] = Math.max(0.001f, scale[index] + (float) delta);
                } else {
                    // A world axis stretches each local axis by how much it lines up with it.
                    Axis[] axes = {Axis.X, Axis.Y, Axis.Z};
                    for (int i = 0; i < 3; i++) {
                        double alignment = localAxis(dragStart, axes[i]).dot(dragAxis);
                        scale[i] = Math.max(0.001f, scale[i] + (float) (delta * alignment * alignment));
                    }
                }
                yield with(dragStart, null, null, scale, null, null);
            }
            case SCALE_UNIFORM -> {
                float factor = (float) Math.max(0.001,
                        1 + delta / Math.max(0.05, scaleAt(dragOrigin) * 3));
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

    /** The shape's pitch/yaw/roll after turning it {@code degrees} about a world-space axis. */
    public static float[] rotatedAbout(ShapeState state, Vec3 worldAxis, double degrees) {
        Quaternionf parent = parentWorldTransform(state).getNormalizedRotation(new Quaternionf());
        Quaternionf turned = new Quaternionf().rotateAxis((float) Math.toRadians(degrees),
                worldAxis.toVector3f().normalize()).mul(rotation(state));
        Vector3f euler = parent.invert().mul(turned).getEulerAnglesXYZ(new Vector3f());
        double[] start = {state.pitch(), state.yaw(), state.roll()};
        double x = Math.toDegrees(euler.x), y = Math.toDegrees(euler.y), z = Math.toDegrees(euler.z);
        // XYZ angles have a second solution for the same orientation; keep whichever stays nearer the old
        // angles so keyframe interpolation doesn't take the long way round.
        double[] first = nearest(new double[]{x, y, z}, start);
        double[] second = nearest(new double[]{x + 180, 180 - y, z + 180}, start);
        double[] best = distance(first, start) <= distance(second, start) ? first : second;
        return new float[]{(float) best[0], (float) best[1], (float) best[2]};
    }

    private static double[] nearest(double[] angles, double[] reference) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = angles[i] + Math.round((reference[i] - angles[i]) / 360.0) * 360.0;
        }
        return result;
    }

    private static double distance(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }

    private static ShapeState withPointDelta(ShapeState state, int pointIndex, Vec3 worldDelta) {
        List<ShapePoint> points = new ArrayList<>(state.points());
        Vec3 pointDelta = absolutePoint(state, pointIndex) ? worldDelta : worldDeltaToLocal(state, worldDelta);
        ShapePoint point = points.get(pointIndex);
        points.set(pointIndex, movedPoint(state, pointIndex, point, pointDelta));
        return with(state, null, null, null, null, points);
    }

    private static ShapePoint movedPoint(ShapeState state, int index, ShapePoint point, Vec3 delta) {
        double x = point.x() + delta.x, y = point.y() + delta.y, z = point.z() + delta.z;
        return absolutePoint(state, index)
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
    public static ShapeState withPosition(ShapeState state, Vec3 worldPosition) {
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
        return ShapeTrackRegistry.parentTransform(state);
    }

    public static Vec3 center(ShapeState state) {
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

    public static ShapeState withRotation(ShapeState state, float[] rotation) {
        return with(state, null, rotation, null, null, null);
    }

    public static ShapeState withScale(ShapeState state, float[] scale) {
        return with(state, null, null, scale, null, null);
    }

    public static Vec3 localAxis(ShapeState state, int axis) {
        return localAxis(state, Axis.values()[axis]);
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

    public static double axisParameter(RayModelIntersection.Ray ray, Vec3 origin, Vec3 axis) {
        Vec3 direction = ray.direction.normalize();
        double dot = axis.dot(direction);
        double denominator = 1 - dot * dot;
        if (Math.abs(denominator) < 1.0e-5) return 0;
        Vec3 offset = origin.subtract(ray.origin);
        return (dot * direction.dot(offset) - axis.dot(offset)) / denominator;
    }

    public static Vec3 intersectPlane(RayModelIntersection.Ray ray, Vec3 point, Vec3 normal) {
        double denominator = ray.direction.dot(normal);
        if (Math.abs(denominator) < 1.0e-6) return null;
        double distance = point.subtract(ray.origin).dot(normal) / denominator;
        return ray.origin.add(ray.direction.scale(distance));
    }

    public static double angleOnPlane(Vec3 value, Vec3 normal) {
        Vec3 reference = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 first = normal.cross(reference).normalize();
        Vec3 second = normal.cross(first).normalize();
        return Math.atan2(value.dot(second), value.dot(first));
    }

    public static double wrapAngle(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2;
        while (angle < -Math.PI) angle += Math.PI * 2;
        return angle;
    }

    /** Inside the viewport rect and not over an ImGui window drawn on top of it (see EditorCameraController). */
    public static boolean mouseInViewport() {
        var mouse = ReplayUI.getMouseViewportFraction();
        return ReplayUI.isActive() && mouse != null
                && mouse.x >= 0 && mouse.x <= 1 && mouse.y >= 0 && mouse.y <= 1
                && ReplayUIAccessor.vector3$isFrameHovered();
    }

    public static Vec3 unboundedMouseLookVector() {
        if (ReplayUI.lastProjectionMatrix == null || ReplayUI.lastViewQuaternion == null) return null;
        var mouse = ReplayUI.getMouseViewportFraction();
        if (mouse == null) return null;
        Vector4f projected = new Vector4f(mouse.x * 2 - 1, mouse.y * 2 - 1, 0, 1)
                .mul(new Matrix4f(ReplayUI.lastProjectionMatrix).invert());
        return ReplayUI.getMouseLookVectorFromForwards(
                new Vec3(projected.x, -projected.y, projected.z).normalize());
    }

    private final GizmoTypedInput typedInput = new GizmoTypedInput();
    private ShapeState typedStart;
    private Map<GroupTransform.Member, ShapeState> typedGroupStart;

    private void typedValue(boolean inViewport) {
        boolean editing = dragging == null && !isScreenSpace() && (grouped() || keyframe != null);
        typedInput.frame(inViewport, editing ? typedTarget : null);
    }

    private final GizmoTypedInput.Target typedTarget = new GizmoTypedInput.Target() {
        @Override public Object identity() { return grouped() ? group : keyframe; }

        @Override
        public void begin() {
            typedStart = keyframe == null ? null : keyframe.value;
            typedGroupStart = grouped() ? currentGroupStates() : null;
        }

        @Override
        public void preview(GizmoMode mode, GizmoTypedInput.Axis axis, double value) {
            if (typedGroupStart != null) {
                Vec3 pivot = groupPivot();
                Map<GroupTransform.Member, ShapeState> states = transformGroup(typedGroupStart,
                        start -> typedTransform(start, pivot, mode, axis, value));
                groupPreview = states;
                previewState = states.get(group.getFirst());
                previewGroup(states);
            } else if (typedStart != null) {
                previewState = typedTransform(typedStart, center(typedStart), mode, axis, value);
                ShapeTrackRegistry.apply(previewState);
            }
            refreshTyped();
        }

        @Override
        public void restore() {
            if (typedGroupStart != null) previewGroup(typedGroupStart);
            else if (typedStart != null) ShapeTrackRegistry.apply(typedStart);
            groupPreview = null;
            previewState = null;
            refreshTyped();
        }

        @Override
        public void commit() {
            if (groupPreview != null) groupCommit.accept(groupPreview);
            else if (previewState != null) commit.accept(previewState);
            groupPreview = null;
            previewState = null;
        }
    };

    private void refreshTyped() {
        ShapeState shown = previewState != null ? previewState : typedStart;
        if (shown == null) return;
        updateHandles(shown);
        updateAabbMarker(shown);
        updateAreaSelectionMarker(shown);
        if (typedGroupStart != null) updateGroupMarkers();
    }

    private ShapeState typedTransform(ShapeState start, Vec3 pivot, GizmoMode mode, GizmoTypedInput.Axis typed, double value) {
        Axis axis = Axis.valueOf(typed.name());
        int index = index(axis);
        Vec3 worldAxis = axis == Axis.NONE ? null : axis(axis);
        return switch (mode) {
            case MOVE -> axis == Axis.NONE ? start : GroupTransform.move(start,
                    (localSpace ? localAxis(start, axis) : worldAxis).scale(value));
            case ROTATE -> axis == Axis.NONE ? start : GroupTransform.rotate(start, pivot, worldAxis, index, value, localSpace);
            case SCALE -> GroupTransform.scale(start, pivot, worldAxis, index, Math.max(0.001, value), localSpace);
            default -> start;
        };
    }
}
