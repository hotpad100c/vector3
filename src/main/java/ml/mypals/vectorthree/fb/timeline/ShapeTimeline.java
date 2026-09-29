package ml.mypals.vectorthree.fb.timeline;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiKey;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import ml.mypals.vectorthree.core.camera.orbit.OrbitTilt;
import ml.mypals.vectorthree.core.fade.ScreenVFX;
import ml.mypals.vectorthree.core.pose.EntityPose;
import ml.mypals.vectorthree.core.shape.ShapeState;
import ml.mypals.vectorthree.core.shape.ShapeTimelineSelection;
import ml.mypals.vectorthree.fb.Editors;
import ml.mypals.vectorthree.fb.channel.ChannelMasks;
import ml.mypals.vectorthree.fb.clips.ClipsWindow;
import ml.mypals.vectorthree.fb.custom.CustomKeyframe;
import ml.mypals.vectorthree.fb.editor.Eyedropper;
import ml.mypals.vectorthree.fb.editor.PropertiesWindow;
import ml.mypals.vectorthree.fb.expression.ExpressionEditor;
import ml.mypals.vectorthree.fb.fade.ScreenVFXKeyframeType;
import ml.mypals.vectorthree.fb.multiedit.GroupTransform;
import ml.mypals.vectorthree.fb.multiedit.MultiSelection;
import ml.mypals.vectorthree.fb.pose.EntityPoseKeyframeType;
import ml.mypals.vectorthree.fb.prefab.PrefabBasketWindow;
import ml.mypals.vectorthree.fb.shape.GizmoMode;
import ml.mypals.vectorthree.fb.shape.ShapeCommands;
import ml.mypals.vectorthree.fb.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframe;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframeType;
import ml.mypals.vectorthree.fb.shape.ShapeManagerWindow;
import ml.mypals.vectorthree.fb.shape.ShapeReparent;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.mixin.flashback.GrabMovementInfoAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.joml.Vector3d;

public final class ShapeTimeline {
    private ShapeTimeline() {}

    static GrabMovementInfoAccessor liveGrab;
    static int liveGrabTotalTicks;
    static boolean previewedDrag;

    public static void captureGrab(GrabMovementInfoAccessor movement, int totalTicks) {
        liveGrab = movement;
        liveGrabTotalTicks = totalTicks;
    }
    static int lastEditorModCount = -1;
    static boolean refreshKeyframes;

    public static void frame() {
        TrackManagement.useScene(Timeline.scene());
        // Gizmos draw with the see-through managers even when no shape was ever applied.
        ShapeTrackRegistry.fixSeeThroughPipelines();
        GizmoMode.pollShortcuts(Editors.ORBIT_GIZMO.isDragging() || Editors.CAMERA_GIZMO.isDragging()
                || Editors.POSE_GIZMO.isDragging() || Editors.GIZMO_EDITOR.isDragging() || Editors.PREFABS.isDragging());
        Editors.ORBIT_GIZMO.frame();
        Editors.CAMERA_GIZMO.frame();
        syncFocusPlaneSelection();
        Editors.FOCUS_GIZMO.frame();
        Editors.POSE_GIZMO.frame();
        shapeShortcuts();
        Editors.GIZMO_EDITOR.frame();
        TrackTimeline.handlePrefabs();
        if (ShapeManagerWindow.isEditorMode()) Editors.EDITOR_CAMERA.frame();
        followPlayhead();
        String shapeId = ShapeTimelineSelection.consume();
        if (shapeId != null && ShapeManagerWindow.isAutoKey()) {
            beginAutoKey(shapeId);
        } else if (shapeId != null) {
            int cursor = TimelineWindow.getCursorTick();
            int bestTrack = -1;
            int bestTick = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int trackIndex = 0; trackIndex < Timeline.scene().keyframeTracks.size(); trackIndex++) {
                KeyframeTrack track = Timeline.scene().keyframeTracks.get(trackIndex);
                if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
                for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                    if (!(entry.getValue() instanceof ShapeKeyframe shape)
                            || !shape.value.shapeId().equals(shapeId)) continue;
                    int distance = Math.abs(entry.getKey() - cursor);
                    if (distance < bestDistance) {
                        bestTrack = trackIndex;
                        bestTick = entry.getKey();
                        bestDistance = distance;
                    }
                }
            }
            if (bestTrack >= 0) {
                Timeline.selected().clear();
                IntSet ticks = new IntOpenHashSet();
                ticks.add(bestTick);
                Timeline.selected().add(new SelectedKeyframes(ShapeKeyframeType.INSTANCE, bestTrack, ticks));
                Timeline.setEditingTrack(bestTrack);
                Timeline.setEditingTick(bestTick);
                PropertiesWindow.requestFocus();
            }
        }

        syncGizmoSelection();
    }

    // Flashback only moves dragged keyframes on release; this applies the moved scene once per frame and puts
    // the scene back. applyKeyframes takes the scene's read lock itself (StampedLock isn't reentrant), so it
    // must run between the two write-locked steps, not inside one.
    public static void previewDrag() {
        GrabMovementInfoAccessor movement = liveGrab;
        liveGrab = null;
        if (!ShapeManagerWindow.isInstantPreview() || !Timeline.grabbedKeyframe() || movement == null || !ImGui.isMouseDown(0)) {
            if (previewedDrag) {
                previewedDrag = false;
                ((MinecraftExt) Minecraft.getInstance()).flashback$applyKeyframes();
            }
            return;
        }
        int delta = movement.vector3$delta(), pivot = movement.vector3$scalePivot(), totalTicks = liveGrabTotalTicks;
        float factor = movement.vector3$scaleFactor();
        Map<KeyframeTrack, TreeMap<Integer, Keyframe>> originals = new java.util.IdentityHashMap<>();
        long stamp = Timeline.state().acquireWrite();
        try {
            List<KeyframeTrack> tracks = Timeline.state().getCurrentScene(stamp).keyframeTracks;
            for (SelectedKeyframes selected : Timeline.selected()) {
                if (selected.trackIndex() >= tracks.size()) continue;
                KeyframeTrack track = tracks.get(selected.trackIndex());
                TreeMap<Integer, Keyframe> original = originals.computeIfAbsent(track, t -> t.keyframesByTick);
                TreeMap<Integer, Keyframe> moved = new TreeMap<>(track.keyframesByTick);
                for (int tick : selected.keyframeTicks()) moved.remove(tick);
                for (int tick : selected.keyframeTicks()) {
                    Keyframe keyframe = original.get(tick);
                    if (keyframe == null) continue;
                    int to = pivot >= 0 ? pivot + Math.round((tick - pivot) * factor) : tick + delta;
                    moved.put(Math.clamp(to, 0, totalTicks), keyframe);
                }
                track.keyframesByTick = moved;
            }
        } finally {
            Timeline.state().release(stamp);
        }
        try {
            Timeline.state().applyKeyframes(new MinecraftKeyframeHandler(Minecraft.getInstance()), TimelineWindow.getCursorTick());
        } finally {
            long restore = Timeline.state().acquireWrite();
            try {
                originals.forEach((track, keyframes) -> track.keyframesByTick = keyframes);
            } finally {
                Timeline.state().release(restore);
            }
        }
        previewedDrag = true;
    }

    public static void afterRender() {
        Eyedropper.endFrame();
        ShapeManagerWindow.render();
        PrefabBasketWindow.render(!Timeline.selected().isEmpty());
        ClipsWindow.render(ClipTimeline.clipsDirty);
        ClipsWindow.renderProgress();
        ml.mypals.vectorthree.fb.camera.CameraPreview.render();
        ml.mypals.vectorthree.fb.editor.HelpWindow.render();
        ml.mypals.vectorthree.fb.editor.HistoryWindow.render();
        ml.mypals.vectorthree.fb.editor.TrackManagerWindow.render();
        ExpressionEditor.render();
        Editors.PREFABS.renderPanel();
        if (ShapeTimelineSelection.consumeRefresh()) refreshKeyframes = true;
        if (Timeline.state() == null) return;
        previewDrag();
   
        if (lastEditorModCount != Timeline.state().modCount) {
            lastEditorModCount = Timeline.state().modCount;
            refreshKeyframes = true;
        }
        if (!refreshKeyframes) return;
        refreshKeyframes = false;
        Timeline.state().applyKeyframes(ShapeKeyframeType.REFRESH_HANDLER, TimelineWindow.getCursorTick());
        pruneDeletedShapes();
    }

    // Runs after render() has released editorScene, so the scene is read under its own lock.
    public static void pruneDeletedShapes() {
        Set<String> liveShapeIds = new HashSet<>();
        long stamp = Timeline.state().acquireRead();
        try {
            for (KeyframeTrack track : Timeline.state().getCurrentScene(stamp).keyframeTracks) {
                if (track.keyframeType != ShapeKeyframeType.INSTANCE || !track.enabled) continue;
                for (Keyframe keyframe : track.keyframesByTick.values()) {
                    if (keyframe instanceof ShapeKeyframe shape) liveShapeIds.add(shape.value.shapeId());
                }
            }
        } finally {
            Timeline.state().release(stamp);
        }
        ShapeTrackRegistry.retainOnly(liveShapeIds);
    }

    // Auto key: a shape picked with nothing selected is edited at the playhead, through a stand-in keyframe
    // holding its interpolated state; the first commit turns it into a real keyframe at the playhead.
    static ShapeKeyframe autoKeyframe;
    static int autoKeyTrack;
    static String autoKeyShape;

    public static void beginAutoKey(String shapeId) {
        int cursor = TimelineWindow.getCursorTick();
        int trackIndex = -1;
        for (int i = 0; i < Timeline.scene().keyframeTracks.size(); i++) {
            KeyframeTrack track = Timeline.scene().keyframeTracks.get(i);
            if (track.keyframeType == ShapeKeyframeType.INSTANCE && !track.keyframesByTick.isEmpty()
                    && track.keyframesByTick.firstEntry().getValue() instanceof ShapeKeyframe first
                    && first.value.shapeId().equals(shapeId)) {
                trackIndex = i;
                break;
            }
        }
        if (trackIndex < 0) return;
        if (Timeline.scene().keyframeTracks.get(trackIndex).keyframesByTick.containsKey(cursor)) {
            autoKeyframe = null;
            selectKeyframe(trackIndex, cursor);
            return;
        }
        ShapeState state = ShapeReparent.stateAt(Timeline.scene(), shapeId, cursor);
        if (state == null) return;
        Timeline.selected().clear();
        autoKeyframe = new ShapeKeyframe(state);
        autoKeyTrack = trackIndex;
        autoKeyShape = shapeId;
        Editors.GIZMO_EDITOR.select(autoKeyframe, ShapeTimeline::commitAutoKey);
    }

    public static void followPlayhead() {
        if (autoKeyframe == null || Editors.GIZMO_EDITOR.isDragging() || Timeline.scene() == null) return;
        ShapeState now = ShapeReparent.stateAt(Timeline.scene(), autoKeyShape, TimelineWindow.getCursorTick());
        if (now != null) autoKeyframe.value = now;
    }

    public static void commitAutoKey(ShapeState state) {
        int trackIndex = autoKeyTrack, tick = TimelineWindow.getCursorTick();
        autoKeyframe = null;
        if (Timeline.scene() == null || trackIndex >= Timeline.scene().keyframeTracks.size()) return;
        Timeline.upgradeToSceneWrite();
        Keyframe existing = Timeline.scene().keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        EditorSceneHistoryAction undo = existing != null
                ? new EditorSceneHistoryAction.SetKeyframe(ShapeKeyframeType.INSTANCE, trackIndex, tick, existing.copy())
                : new EditorSceneHistoryAction.RemoveKeyframe(ShapeKeyframeType.INSTANCE, trackIndex, tick);
        Timeline.scene().push(new EditorSceneHistoryEntry(List.of(undo), List.of(new EditorSceneHistoryAction.SetKeyframe(
                ShapeKeyframeType.INSTANCE, trackIndex, tick, new ShapeKeyframe(state))), I18n.get("vector3.history.auto_key")));
        Timeline.state().markDirty();
        selectKeyframe(trackIndex, tick);
    }

    public static void selectKeyframe(int trackIndex, int tick) {
        Timeline.selected().clear();
        IntSet ticks = new IntOpenHashSet();
        ticks.add(tick);
        Timeline.selected().add(new SelectedKeyframes(ShapeKeyframeType.INSTANCE, trackIndex, ticks));
        Timeline.setEditingTrack(trackIndex);
        Timeline.setEditingTick(tick);
        PropertiesWindow.requestFocus();
    }

    // Over the viewport or the Shape Manager, Ctrl+D duplicates the selected shapes and Delete removes them whole;
    // over the timeline, Delete keeps deleting just the selected keyframes.
    public static void shapeShortcuts() {
        if (ImGui.getIO().getWantTextInput() || Timeline.selected().isEmpty()
                || Editors.GIZMO_EDITOR.isDragging() || Editors.CAMERA_GIZMO.isDragging()) return;
        if (!ShapeGizmoEditor.mouseInViewport() && !ShapeManagerWindow.isHovered()) return;
        boolean duplicate = ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.D, false);
        boolean delete = ImGui.isKeyPressed(ImGuiKey.Delete, false);
        if (!duplicate && !delete) return;
        Set<String> shapes = ShapeCommands.shapesIn(Timeline.scene(), Timeline.selected());
        if (shapes.isEmpty()) return;
        Timeline.upgradeToSceneWrite();
        if (duplicate) {
            ShapeCommands.Duplicate copy = ShapeCommands.duplicate(Timeline.scene(), shapes);
            if (copy == null) return;
            Timeline.scene().push(copy.entry());
            Timeline.selected().clear();
            Timeline.selected().addAll(copy.selection());
        } else {
            EditorSceneHistoryEntry removal = ShapeCommands.delete(Timeline.scene(), shapes);
            if (removal == null) return;
            Timeline.scene().push(removal);
            Timeline.selected().clear();
            Timeline.setEditingTrack(-1);
            Timeline.setEditingTick(-1);
        }
        Timeline.keyframesChanged();
    }

    public static void selectShapeGroup() {
        List<GroupTransform.Member> members = new ArrayList<>();
        for (MultiSelection.Entry entry : MultiSelection.gather(Timeline.scene(), Timeline.selected(),
                Timeline.editingTrack(), Timeline.editingTick())) {
            if (entry.original instanceof ShapeKeyframe shape) members.add(new GroupTransform.Member(shape, entry.track, entry.tick));
        }
        if (members.isEmpty()) {
            Editors.GIZMO_EDITOR.clearSelection();
            return;
        }
        Editors.GIZMO_EDITOR.selectGroup(members, states -> {
            Timeline.upgradeToSceneWrite();
            List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
            for (Map.Entry<GroupTransform.Member, ShapeState> changed : states.entrySet()) {
                GroupTransform.Member member = changed.getKey();
                if (changed.getValue().equals(member.keyframe().value)) continue;
                ShapeKeyframe replacement = (ShapeKeyframe) member.keyframe().copy();
                replacement.value = changed.getValue();
                undo.add(new EditorSceneHistoryAction.SetKeyframe(ShapeKeyframeType.INSTANCE, member.track(), member.tick(),
                        member.keyframe().copy()));
                redo.add(new EditorSceneHistoryAction.SetKeyframe(ShapeKeyframeType.INSTANCE, member.track(), member.tick(),
                        replacement));
            }
            if (redo.isEmpty()) return;
            Timeline.scene().push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.multi_edit", redo.size())));
            Timeline.keyframesChanged();
        });
    }

    public static void syncGizmoSelection() {
        if (Editors.GIZMO_EDITOR.isDragging() || Editors.ORBIT_GIZMO.isDragging() || Editors.CAMERA_GIZMO.isDragging()
                || Editors.POSE_GIZMO.isDragging() || Editors.PREFABS.isDragging()) {
            return;
        }
        syncFocusPlaneSelection();
        if (autoKeyframe != null) {
            boolean cancelled = !ShapeManagerWindow.isAutoKey()
                    || ImGui.isKeyPressed(ImGuiKey.Escape) && !ImGui.getIO().getWantTextInput();
            if (!cancelled && Timeline.selected().isEmpty()) return;
            autoKeyframe = null;
        }
        if (MultiSelection.count(Timeline.selected()) > 1) {
            Editors.ORBIT_GIZMO.clearSelection();
            Editors.CAMERA_GIZMO.clearSelection();
            Editors.POSE_GIZMO.clearSelection();
            selectShapeGroup();
            return;
        }
        if (Timeline.selected().size() != 1
                || Timeline.selected().getFirst().keyframeTicks().size() != 1) {
            Editors.GIZMO_EDITOR.clearSelection();
            Editors.ORBIT_GIZMO.clearSelection();
            Editors.CAMERA_GIZMO.clearSelection();
            Editors.POSE_GIZMO.clearSelection();
            return;
        }
        SelectedKeyframes selected = Timeline.selected().getFirst();
        int trackIndex = selected.trackIndex();
        if (trackIndex < 0 || trackIndex >= Timeline.scene().keyframeTracks.size()) {
            Editors.GIZMO_EDITOR.clearSelection();
            Editors.ORBIT_GIZMO.clearSelection();
            Editors.CAMERA_GIZMO.clearSelection();
            Editors.POSE_GIZMO.clearSelection();
            return;
        }
        int tick = selected.keyframeTicks().iterator().nextInt();
        Keyframe keyframe = Timeline.scene().keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        if (keyframe instanceof CameraOrbitKeyframe orbitKeyframe) {
            Editors.GIZMO_EDITOR.clearSelection();
            Editors.CAMERA_GIZMO.clearSelection();
            Editors.POSE_GIZMO.clearSelection();
            Editors.ORBIT_GIZMO.select(orbitKeyframe, orbit -> {
                CameraOrbitKeyframe replacement = new CameraOrbitKeyframe(new Vector3d(orbit.center()),
                        (float) orbit.distance(), (float) orbit.yaw(), (float) orbit.pitch(),
                        orbitKeyframe.interpolationType());
                ((OrbitTilt) replacement).vector3$setTilt((float) orbit.tiltX(), (float) orbit.tiltZ());
                Timeline.upgradeToSceneWrite();
                Timeline.scene().setKeyframe(trackIndex, tick, replacement);
                EditorStateManager.getCurrent().markDirty();
            });
            return;
        }
        Editors.ORBIT_GIZMO.clearSelection();
        if (keyframe instanceof CameraKeyframe cameraKeyframe) {
            Editors.GIZMO_EDITOR.clearSelection();
            Editors.CAMERA_GIZMO.select(cameraKeyframe, pose -> {
                CameraKeyframe replacement = (CameraKeyframe) cameraKeyframe.copy();
                replacement.position.set(pose.position());
                replacement.yaw = pose.yaw();
                replacement.pitch = pose.pitch();
                replacement.roll = pose.roll();
                ml.mypals.vectorthree.fb.channel.ChannelMasks.markChanged(cameraKeyframe, replacement);
                Timeline.upgradeToSceneWrite();
                Timeline.scene().setKeyframe(trackIndex, tick, replacement);
                EditorStateManager.getCurrent().markDirty();
            });
            return;
        }
        Editors.CAMERA_GIZMO.clearSelection();
        if (keyframe instanceof CustomKeyframe<?> custom && custom.type() == EntityPoseKeyframeType.INSTANCE) {
            Editors.GIZMO_EDITOR.clearSelection();
            @SuppressWarnings("unchecked") CustomKeyframe<EntityPose> pose = (CustomKeyframe<EntityPose>) custom;
            Editors.POSE_GIZMO.select(pose, replacement -> {
                CustomKeyframe<EntityPose> copy = (CustomKeyframe<EntityPose>) pose.copy();
                copy.value = replacement;
                ml.mypals.vectorthree.fb.channel.ChannelMasks.markChanged(pose, copy);
                Timeline.upgradeToSceneWrite();
                Timeline.scene().setKeyframe(trackIndex, tick, copy);
                EditorStateManager.getCurrent().markDirty();
                ((MinecraftExt) Minecraft.getInstance()).flashback$applyKeyframes();
            });
            return;
        }
        Editors.POSE_GIZMO.clearSelection();
        if (selected.type() != ShapeKeyframeType.INSTANCE || !(keyframe instanceof ShapeKeyframe shape)) {
            Editors.GIZMO_EDITOR.clearSelection();
            return;
        }
        Editors.GIZMO_EDITOR.select(shape, replacement -> {
            Timeline.upgradeToSceneWrite();
            ShapeKeyframe moved = new ShapeKeyframe(replacement, shape.interpolationType());
            ml.mypals.vectorthree.fb.channel.ChannelMasks.carry(shape, moved);
            Timeline.scene().setKeyframe(trackIndex, tick, moved);
            EditorStateManager.getCurrent().markDirty();
        });
    }

    public static void syncFocusPlaneSelection() {
        if (MultiSelection.count(Timeline.selected()) != 1 || Timeline.selected().size() != 1
                || Timeline.selected().getFirst().keyframeTicks().size() != 1) {
            Editors.FOCUS_GIZMO.clearSelection();
            return;
        }
        SelectedKeyframes selected = Timeline.selected().getFirst();
        int trackIndex = selected.trackIndex();
        if (trackIndex < 0 || trackIndex >= Timeline.scene().keyframeTracks.size()) {
            Editors.FOCUS_GIZMO.clearSelection();
            return;
        }
        int tick = selected.keyframeTicks().iterator().nextInt();
        Keyframe keyframe = Timeline.scene().keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        if (keyframe instanceof CustomKeyframe<?> custom && custom.type() == ScreenVFXKeyframeType.INSTANCE
                && custom.value instanceof ScreenVFX value
                && value.has(ScreenVFX.DOF)) {
            @SuppressWarnings("unchecked") CustomKeyframe<ScreenVFX> focus = (CustomKeyframe<ScreenVFX>) custom;
            Editors.FOCUS_GIZMO.select(focus);
        } else Editors.FOCUS_GIZMO.clearSelection();
    }
}
