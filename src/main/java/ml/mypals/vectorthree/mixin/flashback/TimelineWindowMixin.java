package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.flashback.ShapeManagerWindow;
import ml.mypals.vectorthree.shape.ShapeTimelineSelection;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = TimelineWindow.class, remap = false)
public abstract class TimelineWindowMixin {
    @Shadow private static EditorScene editorScene;
    @Shadow private static EditorState editorState;
    @Shadow @Final private static List<SelectedKeyframes> selectedKeyframesList;
    @Shadow private static int editingKeyframeTrack;
    @Shadow private static int editingKeyframeTick;
    @Shadow private static float x;
    @Shadow private static float y;
    @Shadow private static float width;
    @Shadow private static float height;
    @Shadow private static float mouseX;
    @Shadow private static float mouseY;
    private static int vector3$lastEditorModCount = -1;
    private static boolean vector3$refreshKeyframes;
    @Shadow private static void upgradeToSceneWrite() {
        throw new AssertionError();
    }

    @Inject(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;beginPopup(Ljava/lang/String;)Z",
            shift = At.Shift.BEFORE))
    private static void vector3$selectClickedShape(CallbackInfo ci) {
        // Gizmos draw with the see-through managers even when no shape was ever applied.
        ShapeTrackRegistry.fixSeeThroughPipelines();
        Vector3.ORBIT_GIZMO.frame();
        Vector3.GIZMO_EDITOR.frame();
        if (ShapeManagerWindow.isEditorMode()) Vector3.EDITOR_CAMERA.frame();
        String shapeId = ShapeTimelineSelection.consume();
        if (shapeId != null) {
            int cursor = TimelineWindow.getCursorTick();
            int bestTrack = -1;
            int bestTick = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int trackIndex = 0; trackIndex < editorScene.keyframeTracks.size(); trackIndex++) {
                KeyframeTrack track = editorScene.keyframeTracks.get(trackIndex);
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
                selectedKeyframesList.clear();
                IntSet ticks = new IntOpenHashSet();
                ticks.add(bestTick);
                selectedKeyframesList.add(new SelectedKeyframes(ShapeKeyframeType.INSTANCE, bestTrack, ticks));
                editingKeyframeTrack = bestTrack;
                editingKeyframeTick = bestTick;
                ImGui.openPopup("##KeyframePopup");
            }
        }

        vector3$syncGizmoSelection();
    }

    @Inject(method = "renderInner", at = @At("TAIL"))
    private static void vector3$syncSelectionAfterTimelineInput(CallbackInfo ci) {
        vector3$syncGizmoSelection();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void vector3$refreshShapesAfterTimelineUnlock(CallbackInfo ci) {
        ShapeManagerWindow.render();
        if (ShapeTimelineSelection.consumeRefresh()) vector3$refreshKeyframes = true;
        if (editorState == null) return;
        // Every scene edit (undo/redo, delete, drag, popup edits) bumps modCount through markDirty().
        if (vector3$lastEditorModCount != editorState.modCount) {
            vector3$lastEditorModCount = editorState.modCount;
            vector3$refreshKeyframes = true;
        }
        if (!vector3$refreshKeyframes) return;
        vector3$refreshKeyframes = false;
        editorState.applyKeyframes(ShapeKeyframeType.REFRESH_HANDLER, TimelineWindow.getCursorTick());
        vector3$pruneDeletedShapes();
    }

    private static void vector3$pruneDeletedShapes() {
        if (editorScene == null) return;
        Set<String> liveShapeIds = new HashSet<>();
        for (KeyframeTrack track : editorScene.keyframeTracks) {
            if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
            for (Keyframe keyframe : track.keyframesByTick.values()) {
                if (keyframe instanceof ShapeKeyframe shape) liveShapeIds.add(shape.value.shapeId());
            }
        }
        ShapeTrackRegistry.retainOnly(liveShapeIds);
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseClicked(I)Z"))
    private static boolean vector3$keepViewportClicksOutOfTimeline(int button) {
        return vector3$isMouseInTimeline() && ImGui.isMouseClicked(button);
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseDragging(I)Z"))
    private static boolean vector3$keepViewportDragOutOfTimeline(int button) {
        if (Vector3.GIZMO_EDITOR.isDragging() || Vector3.ORBIT_GIZMO.isDragging()) {
            return false;
        }
        return ImGui.isMouseDragging(button);
    }

    private static void vector3$syncGizmoSelection() {
        if (Vector3.GIZMO_EDITOR.isDragging() || Vector3.ORBIT_GIZMO.isDragging()) {
            return;
        }
        if (selectedKeyframesList.size() != 1
                || selectedKeyframesList.getFirst().keyframeTicks().size() != 1) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.clearSelection();
            return;
        }
        SelectedKeyframes selected = selectedKeyframesList.getFirst();
        int trackIndex = selected.trackIndex();
        if (trackIndex < 0 || trackIndex >= editorScene.keyframeTracks.size()) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.clearSelection();
            return;
        }
        int tick = selected.keyframeTicks().iterator().nextInt();
        Keyframe keyframe = editorScene.keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        if (keyframe instanceof CameraOrbitKeyframe orbitKeyframe) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.select(orbitKeyframe, orbit -> {
                CameraOrbitKeyframe replacement = new CameraOrbitKeyframe(new Vector3d(orbit.center()),
                        (float) orbit.distance(), (float) orbit.yaw(), (float) orbit.pitch(),
                        orbitKeyframe.interpolationType());
                ((OrbitTilt) replacement).vector3$setTilt((float) orbit.tiltX(), (float) orbit.tiltZ());
                upgradeToSceneWrite();
                editorScene.setKeyframe(trackIndex, tick, replacement);
                EditorStateManager.getCurrent().markDirty();
            });
            return;
        }
        Vector3.ORBIT_GIZMO.clearSelection();
        if (selected.type() != ShapeKeyframeType.INSTANCE || !(keyframe instanceof ShapeKeyframe shape)) {
            Vector3.GIZMO_EDITOR.clearSelection();
            return;
        }
        Vector3.GIZMO_EDITOR.select(shape, replacement -> {
            upgradeToSceneWrite();
            editorScene.setKeyframe(trackIndex, tick,
                    new ShapeKeyframe(replacement, shape.interpolationType()));
            EditorStateManager.getCurrent().markDirty();
        });
    }

    private static boolean vector3$isMouseInTimeline() {
        return !ReplayUI.isMainFrameHovered()
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
