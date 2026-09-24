package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.BlockOverrideKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import it.unimi.dsi.fastutil.ints.IntIterator;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.lookto.LookTo;
import ml.mypals.vectorthree.camera.dolly.DollyZoom;
import ml.mypals.vectorthree.camera.target.Target;
import ml.mypals.vectorthree.camera.target.TargetEditor;
import ml.mypals.vectorthree.camera.orbit.OrbitMath;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.shape.ShapeState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public final class PrefabPlacement {
    private enum Kind { PLACE, EDIT, SAVE }

    private static final Color PATH_COLOR = new Color(90, 200, 255, 230);
    private static final Color POINT_COLOR = new Color(255, 170, 60, 230);
    private static final int ORBIT_SAMPLES = 16;

    private final String session = UUID.randomUUID().toString();
    private final PrefabGizmoEditor gizmo = new PrefabGizmoEditor();
    private final List<Shape> previewShapes = new ArrayList<>();
    private final ImString saveName = new ImString("", 128);
    private final ImInt startTick = new ImInt();
    private final float[] timeScale = {1};

    private Kind kind;
    private Prefab prefab;
    private PrefabTemplates.@Nullable Template template;
    private boolean skippedBlockOverrides;
    private boolean confirmRequested;
    private @Nullable String message;
    private @Nullable String editing;
    private Object previewKey;

    public boolean isActive() { return kind != null; }
    public boolean isDragging() { return gizmo.isDragging(); }
    public boolean isHovering() { return kind != null && gizmo.isHovering(); }

    public void beginPlace(Prefab prefab, PrefabTemplates.@Nullable Template template, int tick) {
        clear();
        kind = Kind.PLACE;
        this.prefab = prefab;
        this.template = template;
        startTick.set(tick);
        timeScale[0] = 1;
        Vec3 target = TargetEditor.crosshairTarget();
        gizmo.setTransform(new PrefabTransform(new Vector3d(target.x, target.y, target.z), new Vector3f(), 1));
    }

    /** Re-opens a placed prefab's placement; confirming replaces its tracks. */
    public void beginEdit(PrefabGroup group) {
        clear();
        kind = Kind.EDIT;
        prefab = group.prefab();
        editing = group.id();
        startTick.set(group.startTick());
        timeScale[0] = group.timeScale();
        gizmo.setTransform(group.transform());
    }

    public void beginSave(EditorScene scene, List<SelectedKeyframes> selection) {
        Prefab world = fromSelection(scene, selection);
        if (world.tracks().isEmpty()) return;
        clear();
        kind = Kind.SAVE;
        prefab = world;
        saveName.set("");
        Vector3d origin = new Vector3d();
        List<Vector3d> points = spatialPoints(world, true);
        for (Vector3d point : points) origin.add(point);
        if (!points.isEmpty()) origin.div(points.size());
        gizmo.setTransform(new PrefabTransform(origin, new Vector3f(), 1));
    }

    public void clear() {
        gizmo.clear();
        clearPreview();
        kind = null;
        prefab = null;
        template = null;
        skippedBlockOverrides = false;
        confirmRequested = false;
        message = null;
        editing = null;
    }

    /** Called inside TimelineWindow#renderInner, where the scene may be written after {@code upgradeToWrite}. */
    public void frame(EditorScene scene, EditorState state, Runnable upgradeToWrite) {
        if (kind == null) return;
        gizmo.frame();
        refreshPreview();
        if (!confirmRequested) return;
        confirmRequested = false;
        if (kind == Kind.SAVE) {
            save();
            return;
        }
        upgradeToWrite.run();
        if (kind == Kind.PLACE) place(scene);
        else replace(scene);
        state.markDirty();
        clear();
    }

    public void renderPanel() {
        if (kind == null) return;
        ImGui.setNextWindowSize(340, 0, ImGuiCond.FirstUseEver);
        if (ImGui.begin(I18n.get(switch (kind) { case PLACE -> "vector3.prefab.placing"; case EDIT -> "vector3.prefab.editing"; case SAVE -> "vector3.prefab.saving"; }) + "###vector3_prefab_placement")) {
            ImGui.textUnformatted(prefab.name());
            for (PrefabGizmoEditor.Mode mode : PrefabGizmoEditor.Mode.values()) {
                if (ImGui.radioButton(I18n.get("vector3.prefab.mode." + mode.name().toLowerCase()), gizmo.mode() == mode)) gizmo.setMode(mode);
                ImGui.sameLine();
            }
            ImGui.newLine();
            if (kind != Kind.SAVE) {
                ImGui.inputInt(I18n.get("vector3.prefab.start_tick"), startTick);
                if (template != null && template.edit()) prefab = template.build();
                ImGui.dragFloat(I18n.get("vector3.prefab.time_scale"), timeScale, 0.01f, 0.05f, 20);
            } else {
                ImGui.inputText(I18n.get("vector3.prefab.name"), saveName);
                if (skippedBlockOverrides) ImGui.textDisabled(I18n.get("vector3.prefab.skipped_block_overrides"));
            }
            editTransform();
            if (ImGui.button(I18n.get(kind == Kind.SAVE ? "vector3.prefab.save" : "vector3.prefab.confirm"))) confirmRequested = true;
            ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.keyframe_type.cancel"))) {
                ImGui.end();
                clear();
                return;
            }
            if (message != null) ImGui.textWrapped(message);
        }
        ImGui.end();
    }

    private void editTransform() {
        PrefabTransform transform = gizmo.transform();
        float[] center = {(float) transform.center().x, (float) transform.center().y, (float) transform.center().z};
        float[] rotation = {transform.rotationDegrees().x, transform.rotationDegrees().y, transform.rotationDegrees().z};
        float[] scale = {(float) transform.scale()};
        boolean changed = ImGui.dragFloat3(I18n.get(kind == Kind.SAVE ? "vector3.prefab.origin" : "vector3.prefab.center"), center, 0.05f);
        changed |= ImGui.dragFloat3(I18n.get("vector3.keyframe.rotation"), rotation, 0.5f);
        if (kind != Kind.SAVE) changed |= ImGui.dragFloat(I18n.get("vector3.keyframe.scale"), scale, 0.01f, 0.01f, 1000);
        if (changed) {
            gizmo.setTransform(new PrefabTransform(new Vector3d(center[0], center[1], center[2]),
                    new Vector3f(rotation[0], rotation[1], rotation[2]), Math.max(0.01, scale[0])));
        }
    }

    private void place(EditorScene scene) {
        Prefab world = PrefabCoordinates.toWorld(prefab, gizmo.transform());
        int base = scene.keyframeTracks.size();
        List<EditorSceneHistoryAction> undo = new ArrayList<>();
        for (int i = world.tracks().size() - 1; i >= 0; i--) {
            undo.add(new EditorSceneHistoryAction.RemoveTrack(world.tracks().get(i).type(), base + i));
        }
        EditorSceneHistoryEntry entry = new EditorSceneHistoryEntry(undo, PrefabGroups.add(world, base, startTick.get(), timeScale[0]),
                I18n.get("vector3.prefab.history", world.name()));
        scene.push(entry);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < world.tracks().size(); i++) indices.add(base + i);
        PrefabGroup group = new PrefabGroup(UUID.randomUUID().toString(), prefab.name(), prefab, gizmo.transform(),
                timeScale[0], startTick.get());
        PrefabGroups.groups(scene).put(group.id(), group);
        PrefabGroups.finishTracks(scene, world, indices, group.id());
        PrefabHistory.refreshAdded(scene, entry);
    }

    private void replace(EditorScene scene) {
        PrefabGroups.Span span = null;
        for (PrefabGroups.Span candidate : PrefabGroups.spans(scene)) {
            if (candidate.group().id().equals(editing)) span = candidate;
        }
        if (span == null) return;
        Prefab world = PrefabCoordinates.toWorld(prefab, gizmo.transform());
        List<Integer> indices = new ArrayList<>();
        EditorSceneHistoryEntry entry = PrefabGroups.replace(scene, span, world, startTick.get(), timeScale[0], indices);
        scene.push(entry);
        PrefabGroups.groups(scene).put(editing, span.group().withPlacement(gizmo.transform(), timeScale[0], startTick.get()));
        PrefabGroups.finishTracks(scene, world, indices, editing);
        PrefabHistory.refreshAdded(scene, entry);
    }

    private void save() {
        String name = saveName.get().trim();
        if (name.isEmpty()) {
            message = I18n.get("vector3.prefab.name_required");
            return;
        }
        Prefab relative = PrefabCoordinates.toPrefab(prefab, gizmo.transform());
        try {
            PrefabStore.save(new Prefab(name, relative.tracks()));
            PrefabBasketWindow.reload();
            clear();
        } catch (Exception exception) {
            Vector3.LOGGER.warn("Could not save prefab {}", name, exception);
            message = I18n.get("vector3.prefab.save_failed", exception.getMessage());
        }
    }

    private Prefab fromSelection(EditorScene scene, List<SelectedKeyframes> selection) {
        int first = Integer.MAX_VALUE;
        for (SelectedKeyframes selected : selection) {
            for (IntIterator ticks = selected.keyframeTicks().iterator(); ticks.hasNext(); ) first = Math.min(first, ticks.nextInt());
        }
        Map<Integer, Prefab.Track> tracks = new LinkedHashMap<>();
        skippedBlockOverrides = false;
        for (SelectedKeyframes selected : selection) {
            if (selected.trackIndex() < 0 || selected.trackIndex() >= scene.keyframeTracks.size()) continue;
            KeyframeTrack source = scene.keyframeTracks.get(selected.trackIndex());
            Prefab.Track track = tracks.computeIfAbsent(selected.trackIndex(), index ->
                    new Prefab.Track(source.keyframeType, source.customName, source.customColour, new TreeMap<>()));
            for (IntIterator ticks = selected.keyframeTicks().iterator(); ticks.hasNext(); ) {
                int tick = ticks.nextInt();
                Keyframe keyframe = source.keyframesByTick.get(tick);
                if (keyframe instanceof BlockOverrideKeyframe) skippedBlockOverrides = true;
                else if (keyframe != null) track.keyframes().put(tick - first, keyframe.copy());
            }
        }
        tracks.values().removeIf(track -> track.keyframes().isEmpty());
        return new Prefab(I18n.get("vector3.prefab.selection"), new ArrayList<>(tracks.values()));
    }

    private void refreshPreview() {
        PrefabTransform transform = gizmo.transform();
        Object key = List.of(transform, prefab, kind);
        if (Objects.equals(key, previewKey)) return;
        previewKey = key;
        clearPreview();
        Prefab world = kind == Kind.SAVE ? prefab : PrefabCoordinates.toWorld(prefab, transform);
        double eye = PrefabCoordinates.eyeHeight();
        for (Prefab.Track track : world.tracks()) {
            List<Vec3> path = new ArrayList<>();
            Keyframe previous = null;
            for (Keyframe keyframe : track.keyframes().values()) {
                if (keyframe instanceof CameraKeyframe camera) {
                    path.add(new Vec3(camera.position.x, camera.position.y + eye, camera.position.z));
                } else if (keyframe instanceof CustomKeyframe<?> custom && custom.value instanceof DollyZoom dolly
                        && dolly.target().kind() == Target.Kind.POSITION) {
                    Vec3 target = dolly.target().resolve(0);
                    path.add(DollyZoom.pose(dolly, dolly, 0, target).eye());
                } else if (keyframe instanceof CameraOrbitKeyframe orbit) {
                    if (previous instanceof CameraOrbitKeyframe from) {
                        for (int i = 1; i <= ORBIT_SAMPLES; i++) path.add(orbitEye(from, orbit, (double) i / ORBIT_SAMPLES));
                    } else {
                        path.add(orbitEye(orbit, orbit, 0));
                    }
                }
                previous = keyframe;
            }
            if (path.size() >= 2) {
                addPreview(ShapeGenerator.generateStripLine().vertexes(path).lineWidth(3f).color(PATH_COLOR)
                        .seeThrough(true).build(Shape.RenderingType.BATCH));
            }
        }
        for (Vector3d point : spatialPoints(world, false)) {
            addPreview(ShapeGenerator.generateSphere().radius(0.12f).segments(10).color(POINT_COLOR).seeThrough(true)
                    .pos(new Vec3(point.x, point.y, point.z)).build(Shape.RenderingType.BATCH));
        }
    }

    private static Vec3 orbitEye(CameraOrbitKeyframe from, CameraOrbitKeyframe to, double amount) {
        OrbitTilt a = (OrbitTilt) from, b = (OrbitTilt) to;
        Vector3d center = new Vector3d(from.center).lerp(to.center, amount);
        Vector3d offset = OrbitMath.eyeOffset(lerp(from.yaw, to.yaw, amount), lerp(from.pitch, to.pitch, amount),
                lerp(from.distance, to.distance, amount), lerp(a.vector3$tiltX(), b.vector3$tiltX(), amount),
                lerp(a.vector3$tiltZ(), b.vector3$tiltZ(), amount));
        return new Vec3(center.x + offset.x, center.y + offset.y, center.z + offset.z);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    /** Look To targets, shape roots, orbit centers and, if asked, camera eyes. */
    private static List<Vector3d> spatialPoints(Prefab prefab, boolean cameras) {
        double eye = PrefabCoordinates.eyeHeight();
        List<Vector3d> points = new ArrayList<>();
        for (Prefab.Track track : prefab.tracks()) {
            for (Keyframe keyframe : track.keyframes().values()) {
                if (keyframe instanceof CameraKeyframe camera) {
                    if (cameras) points.add(new Vector3d(camera.position).add(0, eye, 0));
                }
                else if (keyframe instanceof CameraOrbitKeyframe orbit) points.add(new Vector3d(orbit.center));
                else if (keyframe instanceof ShapeKeyframe shape) {
                    ShapeState state = shape.value;
                    if (state.parentShapeId() == null || state.parentShapeId().isEmpty()) points.add(new Vector3d(state.x(), state.y(), state.z()));
                } else if (keyframe instanceof CustomKeyframe<?> custom && custom.value instanceof LookTo look
                        && look.kind() == Target.Kind.POSITION) {
                    points.add(new Vector3d(look.x(), look.y(), look.z()));
                } else if (keyframe instanceof CustomKeyframe<?> custom && custom.value instanceof DollyZoom dolly
                        && dolly.target().kind() == Target.Kind.POSITION) {
                    points.add(new Vector3d(dolly.target().x(), dolly.target().y(), dolly.target().z()));
                }
            }
        }
        return points;
    }

    private void addPreview(Shape shape) {
        ShapeManagers.addShape(Vector3.id("prefab_preview/" + session + "/" + previewShapes.size()), shape);
        previewShapes.add(shape);
    }

    private void clearPreview() {
        for (Shape shape : previewShapes) shape.discard();
        previewShapes.clear();
        ShapeManagers.removeShapes(Vector3.id("prefab_preview/" + session));
        previewKey = null;
    }
}
