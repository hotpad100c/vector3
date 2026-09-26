package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.types.AudioKeyframeType;
import ml.mypals.vectorthree.clips.Trimming;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import imgui.moulberry90.flag.ImGuiMouseCursor;
import ml.mypals.vectorthree.clips.ReplayArchive;
import ml.mypals.vectorthree.clips.ClipRef;
import ml.mypals.vectorthree.clips.ClipKeyframeType;
import ml.mypals.vectorthree.clips.ClipsWindow;
import ml.mypals.vectorthree.clips.ClipProject;
import com.moulberry.flashback.utils.InputHelper;
import ml.mypals.vectorthree.prefab.PrefabGroup;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.flashback.ShapeCommands;
import ml.mypals.vectorthree.multiedit.PropertySelection;
import ml.mypals.vectorthree.multiedit.PropertyClipboard;
import ml.mypals.vectorthree.multiedit.MultiEditSession;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import ml.mypals.vectorthree.multiedit.MultiSelection;
import ml.mypals.vectorthree.multiedit.MultiPropertiesPage;
import ml.mypals.vectorthree.multiedit.GroupTransform;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import ml.mypals.vectorthree.flashback.curve.SpeedCurves;
import ml.mypals.vectorthree.flashback.curve.SpeedCurveEditor;
import ml.mypals.vectorthree.flashback.curve.SpeedCurve;
import org.spongepowered.asm.mixin.injection.Slice;
import ml.mypals.vectorthree.flashback.PropertiesWindow;
import ml.mypals.vectorthree.flashback.Eyedropper;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeReparent;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import ml.mypals.vectorthree.flashback.skip.SkipKeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.flashback.ShapeManagerWindow;
import ml.mypals.vectorthree.flashback.TrackMove;
import ml.mypals.vectorthree.prefab.PrefabBasketWindow;
import ml.mypals.vectorthree.prefab.PrefabGroups;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.flag.ImGuiKey;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.ext.MinecraftExt;
import net.minecraft.client.Minecraft;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;
import ml.mypals.vectorthree.shape.ShapeTimelineSelection;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

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
    @Shadow private static int keyframeSize;
    @Shadow private static int openCreateKeyframeAtTickTrack;
    @Shadow private static int repositioningKeyframeTrack;
    @Shadow private static boolean grabbedKeyframe;
    @Shadow private static int grabbedKeyframeTrack;
    @Unique
    private static GrabMovementInfoAccessor vector3$liveGrab;
    @Unique
    private static int vector3$liveGrabTotalTicks;
    @Unique
    private static boolean vector3$previewedDrag;
    @Unique
    private static int vector3$lastEditorModCount = -1;
    @Unique
    private static boolean vector3$refreshKeyframes;
    @Shadow private static void upgradeToSceneWrite() {
        throw new AssertionError();
    }
    @Shadow private static int timelineXToReplayTick(float x) {
        throw new AssertionError();
    }
    @Shadow private static int replayTickToTimelineX(int tick) {
        throw new AssertionError();
    }
    @Unique
    private static final List<PrefabGroups.Handle> vector3$groupHandles = new ArrayList<>();
    @Unique
    private static String vector3$draggedGroup;
    @Unique
    private static float vector3$groupDragStartX;
    @Unique
    private static float vector3$groupDragStartY;
    @Unique
    private static PrefabGroups.Drag vector3$groupDrag;
    @Unique
    private static float vector3$timelineX;
    @Unique
    private static String vector3$menuGroup;
    @Unique
    private static boolean vector3$openGroupMenu;
    @Unique
    private static final ImString vector3$groupName = new ImString(128);
    @Unique
    private static List<SelectedKeyframes> vector3$rightClickSelection = List.of();
    @Unique
    private static boolean vector3$openSelectionMenu;

    @Inject(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;beginPopup(Ljava/lang/String;)Z",
            shift = At.Shift.BEFORE))
    private static void vector3$selectClickedShape(CallbackInfo ci) {
        // Gizmos draw with the see-through managers even when no shape was ever applied.
        ShapeTrackRegistry.fixSeeThroughPipelines();
        Vector3.ORBIT_GIZMO.frame();
        Vector3.CAMERA_GIZMO.frame();
        vector3$shapeShortcuts();
        Vector3.GIZMO_EDITOR.frame();
        vector3$handlePrefabs();
        if (ShapeManagerWindow.isEditorMode()) Vector3.EDITOR_CAMERA.frame();
        vector3$followPlayhead();
        String shapeId = ShapeTimelineSelection.consume();
        if (shapeId != null && ShapeManagerWindow.isAutoKey()) {
            vector3$beginAutoKey(shapeId);
        } else if (shapeId != null) {
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
                PropertiesWindow.requestFocus();
            }
        }

        vector3$syncGizmoSelection();
    }

    // Every track row is laid out from contentY, so moving it down frees a band above the top track for the Clips track.
    @ModifyVariable(method = "renderInner", at = @At("STORE"), name = "contentY")
    private static float vector3$clipBand(float contentY) {
        if (editorScene == null) return contentY;
        return contentY + ClipProject.updateBand(editorScene, ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY());
    }

    @ModifyVariable(method = "renderInner", at = @At("STORE"), name = "totalTrackHeight")
    private static float vector3$clipBandHeight(float totalTrackHeight) {
        return totalTrackHeight + ClipProject.band();
    }

    @Unique
    private static boolean vector3$clipsDirty;
    @Unique
    private static int vector3$clipsEnd;

    // Until they are composed, clips can reach past the end of the open replay; the timeline stretches to show them.
    @WrapOperation(method = {"renderInner", "handleClick"}, at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/playback/ReplayServer;getTotalReplayTicks()I"))
    private static int vector3$timelineCoversClips(ReplayServer server, Operation<Integer> original) {
        return Math.max(original.call(server), vector3$clipsEnd);
    }

    // Both the drag preview and the drop read this, so a single dragged clip visibly snaps while it moves.
    @Inject(method = "calculateGrabMovementInfo", at = @At("RETURN"))
    private static void vector3$snapGrabbedClip(int totalTicks, CallbackInfoReturnable<Object> cir) {
        if (editorScene == null || !(cir.getReturnValue() instanceof GrabMovementInfoAccessor movement)) return;
        int delta = movement.vector3$delta(), snapped = vector3$snappedClipDelta(delta, movement.vector3$scalePivot());
        if (snapped != delta) movement.vector3$setDelta(snapped);
    }

    // Its start or end snaps onto the nearest other clip's boundary or the playhead.
    @Unique
    private static int vector3$snappedClipDelta(int delta, int pivot) {
        if (pivot >= 0 || selectedKeyframesList.size() != 1) return delta;
        SelectedKeyframes selected = selectedKeyframesList.getFirst();
        if (selected.type() != ClipKeyframeType.INSTANCE || selected.keyframeTicks().size() != 1) return delta;
        int from = selected.keyframeTicks().iterator().nextInt();
        if (!(editorScene.keyframeTracks.get(selected.trackIndex()).keyframesByTick.get(from)
                instanceof ClipKeyframeType.ClipKeyframe clip)) return delta;
        int threshold = Math.max(1, timelineXToReplayTick(12) - timelineXToReplayTick(0));
        int start = from + delta, length = clip.value.length();
        int toStart = vector3$nearer(ClipProject.snap(editorScene, start, from), TimelineWindow.getCursorTick(), start) - start;
        int toEnd = vector3$nearer(ClipProject.snap(editorScene, start + length, from), TimelineWindow.getCursorTick(), start + length) - (start + length);
        int nudge = Math.abs(toStart) <= Math.abs(toEnd) ? toStart : toEnd;
        return Math.abs(nudge) <= threshold ? Math.max(-from, delta + nudge) : delta;
    }

    @Unique
    private static int vector3$nearer(int a, int b, int tick) {
        return Math.abs(a - tick) <= Math.abs(b - tick) ? a : b;
    }

    @Unique private static final int NO_EDGE = 2;
    @Unique private static int vector3$hoverTrack = -1;
    @Unique private static int vector3$hoverTick = -1;
    @Unique private static int vector3$hoverEdge = NO_EDGE;
    @Unique private static int vector3$trimTrack = -1;
    @Unique private static int vector3$trimTick = -1;
    @Unique private static int vector3$trimOriginalTick;
    @Unique private static boolean vector3$trimLeft;
    @Unique private static Keyframe vector3$trimOriginal;
    @Unique private static Trimming.Range vector3$trimRange;
    @Unique private static float vector3$trimStartX;

    @Unique
    private static boolean vector3$trimmableTrack(KeyframeTrack track) {
        return track.keyframeType == ClipKeyframeType.INSTANCE || track.keyframeType == AudioKeyframeType.INSTANCE;
    }

    // Clips and audio, as in DaVinci: drag an edge to trim, middle-click to cut in two.
    @Unique
    private static void vector3$clipEdges(float x, float rowsY, float lineHeight) {
        vector3$hoverTrack = -1;
        vector3$hoverTick = -1;
        vector3$hoverEdge = NO_EDGE;
        if (vector3$trimTrack >= 0) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            vector3$updateTrim(x);
            return;
        }
        if (!vector3$isMouseInTimeline()) return;
        for (int row = 0; row < editorScene.keyframeTracks.size() && vector3$hoverTrack < 0; row++) {
            KeyframeTrack track = editorScene.keyframeTracks.get(row);
            if (!vector3$trimmableTrack(track)) continue;
            float bottom = rowsY + 2 + (row + 1) * lineHeight;
            float top = bottom - lineHeight - (track.keyframeType == ClipKeyframeType.INSTANCE ? ClipProject.band() : 0);
            if (mouseY < top || mouseY > bottom) continue;
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                float width = entry.getValue().getCustomWidthInTicks();
                if (width <= 0 || !Trimming.trimmable(entry.getValue())) continue;
                float left = x + replayTickToTimelineX(entry.getKey());
                float right = x + replayTickToTimelineX(entry.getKey() + Math.round(width));
                int edge = Math.abs(mouseX - left) <= 5 ? -1 : Math.abs(mouseX - right) <= 5 ? 1
                        : mouseX > left && mouseX < right ? 0 : NO_EDGE;
                if (edge == NO_EDGE) continue;
                vector3$hoverTrack = row;
                vector3$hoverTick = entry.getKey();
                vector3$hoverEdge = edge;
                if (edge != 0) break;
            }
        }
        if (vector3$hoverEdge == -1 || vector3$hoverEdge == 1) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        if (vector3$hoverTick >= 0 && ImGui.isMouseClicked(2)) {
            vector3$split(vector3$hoverTrack, vector3$hoverTick, timelineXToReplayTick(mouseX - x));
        }
    }

    @Unique
    private static void vector3$beginTrim() {
        if (vector3$hoverTrack < 0 || vector3$hoverTrack >= editorScene.keyframeTracks.size()) return;
        Keyframe keyframe = editorScene.keyframeTracks.get(vector3$hoverTrack).keyframesByTick.get(vector3$hoverTick);
        Trimming.Range range = keyframe == null ? null : Trimming.range(keyframe);
        if (range == null) return;
        vector3$trimTrack = vector3$hoverTrack;
        vector3$trimTick = vector3$trimOriginalTick = vector3$hoverTick;
        vector3$trimLeft = vector3$hoverEdge == -1;
        vector3$trimOriginal = keyframe;
        vector3$trimRange = range;
        vector3$trimStartX = mouseX;
    }

    /** The start or end of another media keyframe on {@code track} nearest {@code tick}, or {@code tick} itself. */
    @Unique
    private static int vector3$snapOnTrack(KeyframeTrack track, int tick, int ignore) {
        int best = track.keyframeType == ClipKeyframeType.INSTANCE ? 0 : tick;
        for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
            float width = entry.getValue().getCustomWidthInTicks();
            if (entry.getKey() == ignore || width <= 0) continue;
            for (int boundary : new int[]{entry.getKey(), entry.getKey() + Math.round(width)}) {
                if (Math.abs(boundary - tick) < Math.abs(best - tick)) best = boundary;
            }
        }
        return vector3$nearer(best, TimelineWindow.getCursorTick(), tick);
    }

    @Unique
    private static void vector3$updateTrim(float x) {
        KeyframeTrack track = editorScene.keyframeTracks.get(vector3$trimTrack);
        if (!ImGui.isMouseDown(0)) {
            vector3$finishTrim(track);
            return;
        }
        upgradeToSceneWrite();
        Trimming.Range range = vector3$trimRange;
        int delta = timelineXToReplayTick(mouseX - x) - timelineXToReplayTick(vector3$trimStartX - x);
        int threshold = Math.max(1, timelineXToReplayTick(12) - timelineXToReplayTick(0));
        int from = vector3$trimOriginalTick, tick = from, in = range.in(), out = range.out();
        if (vector3$trimLeft) {
            int edge = from + delta, snapped = vector3$snapOnTrack(track, edge, vector3$trimTick);
            if (Math.abs(snapped - edge) <= threshold) edge = snapped;
            in = Math.clamp(range.in() + edge - from, Math.max(0, range.in() - from), range.out() - 1);
            tick = from + in - range.in();
        } else {
            int edge = from + range.length() + delta, snapped = vector3$snapOnTrack(track, edge, vector3$trimTick);
            if (Math.abs(snapped - edge) <= threshold) edge = snapped;
            out = Math.clamp(range.in() + edge - from, range.in() + 1, range.total());
        }
        if (tick != vector3$trimTick && track.keyframesByTick.containsKey(tick)) return;
        track.keyframesByTick.remove(vector3$trimTick);
        track.keyframesByTick.put(tick, Trimming.withRange(vector3$trimOriginal, in, out));
        vector3$trimTick = tick;
        editorState.markDirty();
    }

    // The live edit is put back first so the history entry does the change itself, and undo can take it back.
    @Unique
    private static void vector3$finishTrim(KeyframeTrack track) {
        int row = vector3$trimTrack, from = vector3$trimOriginalTick, to = vector3$trimTick;
        Keyframe trimmed = track.keyframesByTick.remove(to);
        Keyframe original = vector3$trimOriginal;
        track.keyframesByTick.put(from, original);
        vector3$trimTrack = -1;
        vector3$trimTick = -1;
        if (trimmed == null || trimmed == original) return;
        upgradeToSceneWrite();
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        if (from != to) {
            redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, row, from));
            undo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, row, to));
        }
        redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, to, trimmed.copy()));
        undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, from, original.copy()));
        editorScene.push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.trim_clip")));
        vector3$keyframesChanged();
    }

    /** The blade: the media keyframe at {@code tick} becomes two meeting at {@code at}. */
    @Unique
    private static void vector3$split(int row, int tick, int at) {
        KeyframeTrack track = editorScene.keyframeTracks.get(row);
        Keyframe keyframe = track.keyframesByTick.get(tick);
        Trimming.Range range = keyframe == null ? null : Trimming.range(keyframe);
        if (range == null || at <= tick || at >= tick + range.length()) return;
        int cut = range.in() + at - tick;
        upgradeToSceneWrite();
        List<EditorSceneHistoryAction> undo = List.of(
                new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, row, at),
                new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, tick, keyframe.copy()));
        List<EditorSceneHistoryAction> redo = List.of(
                new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, tick, Trimming.withRange(keyframe, range.in(), cut)),
                new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, at, Trimming.withRange(keyframe, cut, range.out())));
        editorScene.push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.split_clip")));
        selectedKeyframesList.clear();
        vector3$keyframesChanged();
    }

    @Unique
    private static void vector3$handleClips() {
        // Ctrl+B cuts every clip and audio keyframe under the playhead.
        if (!ImGui.getIO().getWantTextInput() && ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.B, false)) {
            int cursor = TimelineWindow.getCursorTick();
            for (int row = 0; row < editorScene.keyframeTracks.size(); row++) {
                KeyframeTrack track = editorScene.keyframeTracks.get(row);
                Map.Entry<Integer, Keyframe> under = vector3$trimmableTrack(track) ? track.keyframesByTick.floorEntry(cursor) : null;
                if (under != null) vector3$split(row, under.getKey(), cursor);
            }
        }
        if (ImGui.getDragDropPayload(ClipsWindow.PAYLOAD) instanceof String path
                && ImGui.isMouseReleased(0) && vector3$isMouseInTimeline()) {
            upgradeToSceneWrite();
            try {
                ClipProject.addClip(editorScene, java.nio.file.Path.of(path), timelineXToReplayTick(mouseX - x));
            } catch (ClipProject.IncompatibleClipException exception) {
                net.minecraft.client.gui.components.toasts.SystemToast.add(Minecraft.getInstance().gui.toastManager(),
                        net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        net.minecraft.network.chat.Component.translatable("vector3.clips.incompatible"),
                        net.minecraft.network.chat.Component.translatable("vector3.clips.incompatible_detail",
                                exception.clipVersion, exception.projectVersion));
            } catch (java.io.IOException exception) {
                Vector3.LOGGER.warn("Could not add clip {}", path, exception);
            }
            // The Clips track may have been inserted at the top, which moves every other track down.
            selectedKeyframesList.clear();
            editingKeyframeTrack = -1;
            editingKeyframeTick = -1;
            vector3$keyframesChanged();
        }
        vector3$clipsDirty = ClipProject.dirty(editorScene);
        vector3$clipsEnd = ClipProject.end(editorScene);
        if (ClipsWindow.consumeApplyRequest() && vector3$clipsDirty) {
            upgradeToSceneWrite();
            try {
                ClipProject.apply(editorState, editorScene);
            } catch (java.io.IOException exception) {
                Vector3.LOGGER.warn("Could not apply the clip changes", exception);
            }
        }
    }

    @Unique
    private static void vector3$handlePrefabs() {
        if (ImGui.getDragDropPayload(PrefabBasketWindow.PAYLOAD) instanceof String id
                && ImGui.isMouseReleased(0) && vector3$isMouseInTimeline()) {
            PrefabBasketWindow.place(id, timelineXToReplayTick(mouseX - x));
        }
        String atCursor = PrefabBasketWindow.consumePlaceAtCursor();
        if (atCursor != null) PrefabBasketWindow.place(atCursor, TimelineWindow.getCursorTick());
        if (PrefabBasketWindow.consumeSaveRequest()) Vector3.PREFABS.beginSave(editorScene, selectedKeyframesList);
        vector3$handleClips();
        Vector3.PREFABS.frame(editorScene, editorState, TimelineWindowMixin::upgradeToSceneWrite);
        vector3$groupMenu();
        vector3$selectionMenu();
    }

    // Flashback previews keyframes while scrubbing the playhead only with Ctrl held; instant preview always does.
    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/utils/InputHelper;isCtrlDownRaw()Z"))
    private static boolean vector3$scrubWithKeyframes(Operation<Boolean> original) {
        return original.call() || ShapeManagerWindow.isInstantPreview();
    }

    // Flashback's keyframe popup becomes the Properties window: begin/end are swapped for the window's, and
    // the popup follows the selection instead of needing a right click.
    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;beginPopup(Ljava/lang/String;)Z"))
    private static boolean vector3$beginProperties(String id, Operation<Boolean> original) {
        if (!PropertiesWindow.KEYFRAME_POPUP.equals(id)) return original.call(id);
        // Undo can remove tracks or keyframes that the persistent window still points at.
        if (editorScene != null) {
            int tracks = editorScene.keyframeTracks.size();
            selectedKeyframesList.removeIf(selected -> selected.trackIndex() >= tracks);
        }
        if (selectedKeyframesList.size() == 1 && selectedKeyframesList.getFirst().keyframeTicks().size() == 1) {
            editingKeyframeTrack = selectedKeyframesList.getFirst().trackIndex();
            editingKeyframeTick = selectedKeyframesList.getFirst().keyframeTicks().iterator().nextInt();
        }
        Keyframe editing = vector3$editingKeyframe();
        if (editing == null) {
            editingKeyframeTrack = -1;
            editingKeyframeTick = -1;
        }
        int selected = MultiSelection.count(selectedKeyframesList);
        if (selected > 1) return PropertiesWindow.begin(true, Long.MIN_VALUE + 1 + selectedKeyframesList.hashCode(), false);
        return PropertiesWindow.begin(editingKeyframeTrack >= 0 && editingKeyframeTick >= 0,
                ((long) editingKeyframeTrack << 32) | (editingKeyframeTick & 0xFFFFFFFFL),
                editing != null && SpeedCurves.of(editing) != null);
    }

    @Unique
    private static MultiPropertiesPage.Host vector3$multiHost() {
        return new MultiPropertiesPage.Host(() -> editorScene, () -> upgradeToSceneWrite(),
                () -> vector3$keyframesChanged(), () -> vector3$setCurves(SpeedCurve.preset(SpeedCurve.Preset.EASE_IN_OUT)),
                () -> Vector3.GIZMO_EDITOR.controls());
    }

    private static Keyframe vector3$editingKeyframe() {
        if (editorScene == null || editingKeyframeTrack < 0 || editingKeyframeTrack >= editorScene.keyframeTracks.size()) {
            return null;
        }
        return editorScene.keyframeTracks.get(editingKeyframeTrack).keyframesByTick.get(editingKeyframeTick);
    }

    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;renderKeyframeOptionsPopup(I)V"))
    private static void vector3$propertiesPage(int totalTicks, Operation<Void> original) {
        if (PropertiesWindow.isCurveTab()) {
            vector3$renderCurvePage();
            return;
        }
        PropertySelection.beginFrame(java.util.Objects.hash(selectedKeyframesList, editingKeyframeTrack, editingKeyframeTick));
        if (MultiSelection.count(selectedKeyframesList) > 1) {
            MultiPropertiesPage.render(selectedKeyframesList, editingKeyframeTrack, editingKeyframeTick, vector3$multiHost());
        } else {
            MultiEditSession.display("single", List.of(), key -> true, false, () -> original.call(totalTicks));
        }
        List<PropertyClipboard.Clip> paste = PropertySelection.frame();
        if (paste != null) {
            List<SelectedKeyframes> targets = selectedKeyframesList;
            if (targets.isEmpty() && editingKeyframeTrack >= 0 && editingKeyframeTrack < editorScene.keyframeTracks.size()) {
                targets = List.of(new SelectedKeyframes(editorScene.keyframeTracks.get(editingKeyframeTrack).keyframeType,
                        editingKeyframeTrack, IntSet.of(editingKeyframeTick)));
            }
            int applied = MultiPropertiesPage.paste(paste, targets, editingKeyframeTrack, editingKeyframeTick, vector3$multiHost());
            PropertySelection.pasted(applied, paste.size());
        }
    }

    // "Custom" joins the interpolation types: choosing it gives the selected keyframes a speed curve.
    @WrapOperation(method = "renderKeyframeOptionsPopup", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;combo(Ljava/lang/String;[I[Ljava/lang/String;)Z"))
    private static boolean vector3$customInterpolation(String label, int[] selected, String[] names,
            Operation<Boolean> original) {
        Keyframe editing = vector3$editingKeyframe();
        boolean custom = editing != null && SpeedCurves.of(editing) != null;
        String[] withCustom = java.util.Arrays.copyOf(names, names.length + 1);
        withCustom[names.length] = I18n.get("vector3.curve.custom");
        if (custom) selected[0] = names.length;
        if (!original.call(label, selected, withCustom)) return false;
        if (selected[0] == names.length) {
            if (!custom) vector3$setCurves(SpeedCurve.preset(SpeedCurve.Preset.EASE_IN_OUT));
            return false;
        }
        vector3$clearCurvesOnPush = true;
        return true;
    }

    private static boolean vector3$clearCurvesOnPush;

    // The keyframes Flashback writes for the new interpolation type are copies, still carrying the curve.
    @WrapOperation(method = "renderKeyframeOptionsPopup", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/moulberry/flashback/state/EditorScene;push(Lcom/moulberry/flashback/state/EditorSceneHistoryEntry;)V"))
    private static void vector3$dropCurvesWithType(EditorScene scene, EditorSceneHistoryEntry entry, Operation<Void> original) {
        if (vector3$clearCurvesOnPush) {
            for (EditorSceneHistoryAction action : entry.redo()) {
                if (action instanceof EditorSceneHistoryAction.SetKeyframe set) SpeedCurves.set(set.keyframe(), null);
            }
            vector3$clearCurvesOnPush = false;
        }
        original.call(scene, entry);
    }

    private static void vector3$setCurves(SpeedCurve curve) {
        upgradeToSceneWrite();
        List<SelectedKeyframes> targets = selectedKeyframesList.isEmpty() && editingKeyframeTrack >= 0
                ? List.of(new SelectedKeyframes(editorScene.keyframeTracks.get(editingKeyframeTrack).keyframeType,
                        editingKeyframeTrack, IntSet.of(editingKeyframeTick)))
                : selectedKeyframesList;
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        for (SelectedKeyframes selected : targets) {
            if (selected.trackIndex() >= editorScene.keyframeTracks.size()) continue;
            KeyframeTrack track = editorScene.keyframeTracks.get(selected.trackIndex());
            if (!track.keyframeType.allowChangingInterpolationType()) continue;
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = track.keyframesByTick.get(tick);
                if (keyframe == null) continue;
                Keyframe curved = keyframe.copy();
                curved.interpolationType(InterpolationType.LINEAR);
                SpeedCurves.set(curved, curve);
                undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selected.trackIndex(), tick, keyframe.copy()));
                redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selected.trackIndex(), tick, curved));
            }
        }
        if (redo.isEmpty()) return;
        editorScene.push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.speed_curve")));
        vector3$keyframesChanged();
    }

    private static void vector3$renderCurvePage() {
        Keyframe keyframe = vector3$editingKeyframe();
        SpeedCurve curve = keyframe == null ? null : SpeedCurves.of(keyframe);
        if (curve == null) return;
        KeyframeTrack track = editorScene.keyframeTracks.get(editingKeyframeTrack);
        Integer next = track.keyframesByTick.higherKey(editingKeyframeTick);
        float playhead = next == null ? -1
                : (TimelineWindow.getCursorTick() - editingKeyframeTick) / (float) (next - editingKeyframeTick);
        SpeedCurveEditor.Result result = SpeedCurveEditor.render(curve, playhead);
        if (result == null) return;
        upgradeToSceneWrite();
        keyframe = vector3$editingKeyframe();
        if (keyframe == null) return;
        SpeedCurves.set(keyframe, result.curve());
        if (result.commit() && !result.curve().equals(result.before())) {
            Keyframe before = keyframe.copy();
            SpeedCurves.set(before, result.before());
            editorScene.push(new EditorSceneHistoryEntry(
                    List.of(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, editingKeyframeTrack, editingKeyframeTick, before)),
                    List.of(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, editingKeyframeTrack, editingKeyframeTick, keyframe.copy())),
                    I18n.get("vector3.history.speed_curve")));
        }
        vector3$keyframesChanged();
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE", ordinal = 0, target = "Limgui/moulberry90/ImGui;endPopup()V"),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;renderKeyframeOptionsPopup(I)V")))
    private static void vector3$endProperties() {
        PropertiesWindow.end();
    }

    // Flashback only tests the mouse position, so clicks on a floating window over the timeline fell through.
    @WrapOperation(method = {"renderInner", "handleClick"}, at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;isMouseClicked(I)Z"))
    private static boolean vector3$clickOnTimelineOnly(int button, Operation<Boolean> original) {
        return original.call(button) && ImGui.isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
    }

    @WrapOperation(method = "handleClick", at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;openPopup(Ljava/lang/String;)V"))
    private static void vector3$openProperties(String id, Operation<Void> original) {
        if (PropertiesWindow.KEYFRAME_POPUP.equals(id)) PropertiesWindow.requestFocus();
        else original.call(id);
    }

    // Flashback's box select feeds window-absolute X where every other caller passes it relative to the window.
    @ModifyArg(method = "releaseGrabbed", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineXToReplayTick(F)I"))
    private static float vector3$boxSelectStart(float timelineX) {
        return timelineX - x;
    }

    @ModifyArg(method = "releaseGrabbed", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineXToReplayTick(F)I"))
    private static float vector3$boxSelectEnd(float timelineX) {
        return timelineX - x;
    }

    @Inject(method = "handleClick", at = @At("HEAD"))
    private static void vector3$rememberSelection(CallbackInfo ci) {
        vector3$rightClickSelection = ImGui.isMouseClicked(1) ? new ArrayList<>(selectedKeyframesList) : List.of();
    }

    @Inject(method = "handleClick", at = @At("RETURN"))
    private static void vector3$restoreSelection(CallbackInfo ci) {
        if (vector3$rightClickSelection.isEmpty() || !selectedKeyframesList.isEmpty()) return;
        selectedKeyframesList.addAll(vector3$rightClickSelection);
        vector3$rightClickSelection = List.of();
        if (openCreateKeyframeAtTickTrack < 0) vector3$openSelectionMenu = true;
    }

    @Inject(method = "renderKeyframeElements", at = @At(value = "CONSTANT", args = "stringValue=flashback.create_keyframe_at_n"))
    private static void vector3$createKeyframePopupItems(float x, float y, int cursorTicks, int middleX, CallbackInfo ci) {
        if (!selectedKeyframesList.isEmpty()) vector3$createGroupItem(-1);
    }

    @Unique
    private static void vector3$selectionMenu() {
        if (vector3$openSelectionMenu) {
            ImGui.openPopup("##vector3SelectionPopup");
            vector3$openSelectionMenu = false;
        }
        if (!ImGui.beginPopup("##vector3SelectionPopup")) return;
        if (selectedKeyframesList.isEmpty()) ImGui.closeCurrentPopup();
        else vector3$createGroupItem(-1);
        ImGui.endPopup();
    }

    // The selected keyframes, or the whole track when nothing is selected.
    @Unique
    private static List<SelectedKeyframes> vector3$groupTargets(int trackIndex) {
        if (!selectedKeyframesList.isEmpty() || trackIndex < 0) return new ArrayList<>(selectedKeyframesList);
        return PrefabGroups.wholeTrack(editorScene, trackIndex);
    }

    @Unique
    private static void vector3$createGroupItem(int trackIndex) {
        if (!ImGui.menuItem("\ue945 " + I18n.get("vector3.prefab.track.create_group"))) return;
        upgradeToSceneWrite();
        PrefabGroups.create(editorScene, I18n.get("vector3.prefab.group.default_name",
                PrefabGroups.groups(editorScene).size() + 1), vector3$groupTargets(trackIndex));
        editorState.markDirty();
    }

    @Unique
    private static void vector3$groupMenu() {
        if (vector3$openGroupMenu) {
            ImGui.openPopup("##vector3PrefabGroup");
            vector3$openGroupMenu = false;
            PrefabGroups.Span opened = vector3$span(vector3$menuGroup);
            vector3$groupName.set(opened == null ? "" : opened.group().name());
        }
        if (!ImGui.beginPopup("##vector3PrefabGroup")) return;
        PrefabGroups.Span span = vector3$span(vector3$menuGroup);
        if (span == null) {
            ImGui.closeCurrentPopup();
        } else {
            if (ImGui.inputText(I18n.get("vector3.prefab.name"), vector3$groupName) && !vector3$groupName.get().isBlank()) {
                upgradeToSceneWrite();
                PrefabGroups.rename(editorScene, span.group(), vector3$groupName.get().trim());
                editorState.markDirty();
            }
            if (span.group().placed() && ImGui.menuItem(I18n.get("vector3.prefab.group.edit"))) {
                Vector3.PREFABS.beginEdit(span.group());
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.save"))) {
                Vector3.PREFABS.beginSave(editorScene, PrefabGroups.selection(editorScene, span));
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.dissolve"))) {
                upgradeToSceneWrite();
                PrefabGroups.dissolve(editorScene, span);
                editorState.markDirty();
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.delete"))) {
                upgradeToSceneWrite();
                editorScene.push(PrefabGroups.delete(editorScene, span));
                editorState.markDirty();
            }
        }
        ImGui.endPopup();
    }

    @Unique
    private static PrefabGroups.Span vector3$span(String groupId) {
        if (groupId == null || editorScene == null) return null;
        for (PrefabGroups.Span span : PrefabGroups.spans(editorScene)) {
            if (span.group().id().equals(groupId)) return span;
        }
        return null;
    }

    @Inject(method = "renderKeyframes", at = @At("HEAD"))
    private static void vector3$drawPrefabGroups(float x, float y, float mouseX, int minTicks, float availableTicks,
            int totalTicks, CallbackInfo ci) {
        vector3$groupHandles.clear();
        vector3$timelineX = x;
        if (editorScene == null) return;
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        float barHeight = lineHeight * 0.3f;
        ImDrawList drawList = ImGui.getWindowDrawList();
        vector3$dragGroup(x, y, mouseX);
        vector3$clipEdges(x, y, lineHeight);
        vector3$drawTrackMoveTargets(drawList, x, y, lineHeight, vector3$trackMovePlan(y));
        vector3$drawTrackMoveTargets(drawList, x, y, lineHeight, vector3$groupMovePlan(y));
        for (PrefabGroups.Span span : PrefabGroups.spans(editorScene)) {
            float left = x + replayTickToTimelineX(span.firstTick()) - 5;
            float right = Math.max(left + 10, x + replayTickToTimelineX(span.lastTick()) + 5);
            int colour = vector3$groupColour(span.group().id());
            for (int row : span.tracks()) {
                float top = y + 2 + row * lineHeight;
                drawList.addRectFilled(left, top, right, top + lineHeight, (colour & 0x00FFFFFF) | 0x28000000, 3);
            }
            float top = y + 2 + span.tracks().getFirst() * lineHeight;
            drawList.addRectFilled(left, top, right, top + barHeight, (colour & 0x00FFFFFF) | 0xD0000000, 2);
            drawList.addText(ImGui.getFont(), (int) Math.max(8, barHeight), left + 3, top - 1, 0xFF000000, span.group().name());
            vector3$groupHandles.add(new PrefabGroups.Handle(span.group().id(), left, top, right, top + barHeight, -1));
            for (int row : span.tracks()) {
                float rowTop = y + 2 + row * lineHeight;
                vector3$groupHandles.add(new PrefabGroups.Handle(span.group().id(), left, rowTop, right, rowTop + lineHeight, row));
            }
        }
    }

    @Unique
    private static int vector3$rowAt(float screenY, float rowsY) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        return (int) Math.floor((screenY - (rowsY + 2)) / lineHeight);
    }

    @Unique
    private static TrackMove.Plan vector3$trackMovePlan(float rowsY) {
        if (!grabbedKeyframe || editorScene == null) return null;
        return TrackMove.plan(editorScene, selectedKeyframesList, vector3$rowAt(mouseY, rowsY) - grabbedKeyframeTrack, null);
    }

    @Unique
    private static TrackMove.Plan vector3$groupMovePlan(float rowsY) {
        if (vector3$groupDrag == null || editorScene == null) return null;
        int rowDelta = vector3$rowAt(mouseY, rowsY) - vector3$rowAt(vector3$groupDragStartY, rowsY);
        return vector3$groupDrag.plan(editorScene, rowDelta);
    }

    @Unique
    private static void vector3$drawTrackMoveTargets(ImDrawList drawList, float x, float y, float lineHeight,
                                                     TrackMove.Plan plan) {
        if (plan == null) return;
        int colour = plan.valid() ? 0x00FFC850 : 0x003C3CFF;
        for (int target : new java.util.TreeSet<>(plan.targets().values())) {
            if (target < 0) continue;
            float top = y + 2 + target * lineHeight;
            drawList.addRectFilled(x, top, x + width, top + lineHeight, colour | 0x30000000, 3);
            drawList.addRect(x, top, x + width, top + lineHeight, colour | 0xA0000000, 3);
            if (plan.creates(target)) {
                drawList.addText(x + 4, top + (lineHeight - ImGui.getTextLineHeight()) / 2, colour | 0xC0000000,
                        I18n.get("vector3.timeline.new_track"));
            }
        }
    }

    // Dropping on another row of the same type moves the keyframes there instead of Flashback's in-track move.
    @Inject(method = "releaseGrabbed", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow$GrabMovementInfo;grabbedScalePivotTick:I"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void vector3$moveAcrossTracks(ReplayServer server, int totalTicks, float rowsY, CallbackInfo ci,
            @Coerce GrabMovementInfoAccessor movement) {
        TrackMove.Plan plan = vector3$trackMovePlan(rowsY);
        int delta = movement.vector3$delta(), pivot = movement.vector3$scalePivot();
        float factor = movement.vector3$scaleFactor();
        IntUnaryOperator retime = tick -> Math.clamp(
                pivot >= 0 ? pivot + Math.round((tick - pivot) * factor) : tick + delta, 0, totalTicks);
        // Alt drops copies where the drag ends and leaves the originals where they were, as in Resolve.
        if (ImGui.getIO().getKeyAlt() && !ImGui.getIO().getKeyCtrl()) {
            List<SelectedKeyframes> selection = new ArrayList<>(selectedKeyframesList);
            TrackMove.Plan target = plan != null && plan.valid() ? plan : TrackMove.inPlace(editorScene, selection);
            List<SelectedKeyframes> copies = new ArrayList<>();
            upgradeToSceneWrite();
            EditorSceneHistoryEntry entry = TrackMove.copyEntry(editorScene, selection, target, retime, null, copies);
            movement.vector3$setDelta(0);
            movement.vector3$setScalePivot(-1);
            if (entry == null) return;
            editorScene.push(entry);
            selectedKeyframesList.clear();
            selectedKeyframesList.addAll(copies);
            vector3$keyframesChanged();
            return;
        }
        if (plan == null || !plan.valid()) return;
        upgradeToSceneWrite();
        List<SelectedKeyframes> moved = new ArrayList<>();
        editorScene.push(TrackMove.entry(editorScene, new ArrayList<>(selectedKeyframesList), plan, retime, moved));
        selectedKeyframesList.clear();
        selectedKeyframesList.addAll(moved);
        // No offset and no scale pivot: Flashback's own move below then has nothing to do.
        movement.vector3$setDelta(0);
        movement.vector3$setScalePivot(-1);
        vector3$keyframesChanged();
    }

    @Unique
    private static void vector3$dragGroup(float x, float rowsY, float mouseX) {
        if (vector3$draggedGroup == null) return;
        if (vector3$groupDrag == null) {
            PrefabGroups.Span span = vector3$span(vector3$draggedGroup);
            if (span == null) {
                vector3$draggedGroup = null;
                return;
            }
            vector3$groupDrag = new PrefabGroups.Drag(editorScene, span);
        }
        upgradeToSceneWrite();
        if (ImGui.isMouseClicked(1) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            vector3$groupDrag.cancel(editorScene);
            vector3$groupDrag = null;
            vector3$draggedGroup = null;
            vector3$keyframesChanged();
            return;
        }
        if (ImGui.isMouseDown(0)) {
            int delta = timelineXToReplayTick(mouseX - x) - timelineXToReplayTick(vector3$groupDragStartX - x);
            if (vector3$groupDrag.update(editorScene, delta)) vector3$keyframesChanged();
            return;
        }
        TrackMove.Plan plan = vector3$groupMovePlan(rowsY);
        if (ImGui.getIO().getKeyAlt()) {
            vector3$copyGroup(plan, timelineXToReplayTick(mouseX - x) - timelineXToReplayTick(vector3$groupDragStartX - x));
            return;
        }
        var entry = plan != null && plan.valid()
                ? vector3$groupDrag.finishAcross(editorScene, plan)
                : vector3$groupDrag.finish(editorScene);
        if (entry != null) editorScene.push(entry);
        vector3$groupDrag = null;
        vector3$draggedGroup = null;
        vector3$keyframesChanged();
    }

    // The drag has been showing the group moving; put it back and drop a copy there instead, as a new group.
    @Unique
    private static void vector3$copyGroup(TrackMove.Plan plan, int delta) {
        PrefabGroups.Span span = vector3$span(vector3$draggedGroup);
        vector3$groupDrag.cancel(editorScene);
        vector3$groupDrag = null;
        vector3$draggedGroup = null;
        if (span != null) {
            List<SelectedKeyframes> selection = PrefabGroups.selection(editorScene, span);
            int shift = Math.max(delta, -span.firstTick());
            TrackMove.Plan target = plan != null && plan.valid() ? plan : TrackMove.inPlace(editorScene, selection);
            String group = java.util.UUID.randomUUID().toString();
            List<SelectedKeyframes> copies = new ArrayList<>();
            EditorSceneHistoryEntry entry = TrackMove.copyEntry(editorScene, selection, target, tick -> tick + shift, group, copies);
            if (entry != null) {
                PrefabGroup source = span.group();
                PrefabGroups.groups(editorScene).put(group, new PrefabGroup(group,
                        I18n.get("vector3.shape.copy_name", source.name()), source.prefab(), source.transform(),
                        source.timeScale(), source.startTick() + shift));
                editorScene.push(entry);
                selectedKeyframesList.clear();
                selectedKeyframesList.addAll(copies);
            }
        }
        vector3$keyframesChanged();
    }

    // Flashback scales the dragged keyframes about a pivot while Alt is held; Alt now copies, so scaling moves to Ctrl+Alt.
    @WrapOperation(method = "calculateGrabMovementInfo", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/utils/InputHelper;isAltDownRaw()Z"))
    private static boolean vector3$scaleWithCtrlAlt(Operation<Boolean> original) {
        return original.call() && InputHelper.isCtrlDownRaw();
    }

    // Alt on a track's drag handle leaves a copy of the track behind; the original is the one being dragged.
    @Inject(method = "renderKeyframeElements", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;repositioningKeyframeTrack:I"))
    private static void vector3$copyDraggedTrack(float x, float y, int cursorTicks, int middleX, CallbackInfo ci) {
        if (!ImGui.getIO().getKeyAlt() || repositioningKeyframeTrack < 0
                || repositioningKeyframeTrack >= editorScene.keyframeTracks.size()) return;
        int index = repositioningKeyframeTrack;
        KeyframeTrack original = editorScene.keyframeTracks.get(index);
        if (original.keyframeType == ClipKeyframeType.INSTANCE || original.keyframeType == SkipKeyframeType.INSTANCE) return;
        upgradeToSceneWrite();
        editorScene.push(TrackMove.copyTrack(editorScene, index));
        KeyframeTrack copy = editorScene.keyframeTracks.get(index);
        copy.enabled = original.enabled;
        copy.customName = original.customName;
        copy.customColour = original.customColour;
        repositioningKeyframeTrack = index + 1;
        selectedKeyframesList.clear();
        vector3$keyframesChanged();
    }

    @Unique
    private static void vector3$keyframesChanged() {
        editorState.markDirty();
        ((MinecraftExt) Minecraft.getInstance()).flashback$applyKeyframes();
    }

    @Unique
    private static int vector3$groupColour(String groupId) {
        int rgb = java.awt.Color.HSBtoRGB((groupId.hashCode() & 0xFFFF) / 65535f, 0.55f, 0.95f);
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    // Empty space in a group's rows counts too, but a click that would hit a keyframe stays Flashback's.
    @Unique
    private static String vector3$groupAt(float mouseX, float mouseY) {
        for (PrefabGroups.Handle handle : vector3$groupHandles) {
            if (mouseX < handle.left() || mouseX > handle.right() || mouseY < handle.top() || mouseY > handle.bottom()) continue;
            if (handle.row() < 0 || !vector3$nearKeyframe(handle.row(), mouseX)) return handle.groupId();
        }
        return null;
    }

    @Unique
    private static boolean vector3$nearKeyframe(int row, float mouseX) {
        if (row >= editorScene.keyframeTracks.size()) return false;
        TreeMap<Integer, Keyframe> keyframes = editorScene.keyframeTracks.get(row).keyframesByTick;
        int tick = timelineXToReplayTick(mouseX - vector3$timelineX);
        Map.Entry<Integer, Keyframe> floor = keyframes.floorEntry(tick);
        if (floor != null && floor.getValue().getCustomWidthInTicks() > 0
                && tick <= floor.getKey() + Math.ceil(floor.getValue().getCustomWidthInTicks())) return true;
        for (Integer key : new Integer[]{floor == null ? null : floor.getKey(), keyframes.ceilingKey(tick)}) {
            if (key != null && Math.abs(vector3$timelineX + replayTickToTimelineX(key) - mouseX) < keyframeSize) return true;
        }
        return false;
    }

    @Inject(method = "renderInner", at = @At("TAIL"))
    private static void vector3$syncSelectionAfterTimelineInput(CallbackInfo ci) {
        vector3$syncGizmoSelection();
    }

    // The same drag offset Flashback draws the grabbed keyframes with this frame.
    @Inject(method = "renderKeyframes", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineWidth:F"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void vector3$captureGrab(float x, float y, float mouseX, int minTicks, float availableTicks,
            int totalTicks, CallbackInfo ci, float lineHeight, ImDrawList drawList,
            @Coerce GrabMovementInfoAccessor movement) {
        vector3$liveGrab = movement;
        vector3$liveGrabTotalTicks = totalTicks;
    }

    // Flashback only moves dragged keyframes on release; this applies the moved scene once per frame and puts
    // the scene back. applyKeyframes takes the scene's read lock itself (StampedLock isn't reentrant), so it
    // must run between the two write-locked steps, not inside one.
    @Unique
    private static void vector3$previewDrag() {
        GrabMovementInfoAccessor movement = vector3$liveGrab;
        vector3$liveGrab = null;
        if (!ShapeManagerWindow.isInstantPreview() || !grabbedKeyframe || movement == null || !ImGui.isMouseDown(0)) {
            if (vector3$previewedDrag) {
                vector3$previewedDrag = false;
                ((MinecraftExt) Minecraft.getInstance()).flashback$applyKeyframes();
            }
            return;
        }
        int delta = movement.vector3$delta(), pivot = movement.vector3$scalePivot(), totalTicks = vector3$liveGrabTotalTicks;
        float factor = movement.vector3$scaleFactor();
        Map<KeyframeTrack, TreeMap<Integer, Keyframe>> originals = new java.util.IdentityHashMap<>();
        long stamp = editorState.acquireWrite();
        try {
            List<KeyframeTrack> tracks = editorState.getCurrentScene(stamp).keyframeTracks;
            for (SelectedKeyframes selected : selectedKeyframesList) {
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
            editorState.release(stamp);
        }
        try {
            editorState.applyKeyframes(new MinecraftKeyframeHandler(Minecraft.getInstance()), TimelineWindow.getCursorTick());
        } finally {
            long restore = editorState.acquireWrite();
            try {
                originals.forEach((track, keyframes) -> track.keyframesByTick = keyframes);
            } finally {
                editorState.release(restore);
            }
        }
        vector3$previewedDrag = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void vector3$refreshShapesAfterTimelineUnlock(CallbackInfo ci) {
        Eyedropper.endFrame();
        ShapeManagerWindow.render();
        PrefabBasketWindow.render(!selectedKeyframesList.isEmpty());
        ClipsWindow.render(vector3$clipsDirty);
        ClipsWindow.renderProgress();
        Vector3.PREFABS.renderPanel();
        if (ShapeTimelineSelection.consumeRefresh()) vector3$refreshKeyframes = true;
        if (editorState == null) return;
        vector3$previewDrag();
       
        if (vector3$lastEditorModCount != editorState.modCount) {
            vector3$lastEditorModCount = editorState.modCount;
            vector3$refreshKeyframes = true;
        }
        if (!vector3$refreshKeyframes) return;
        vector3$refreshKeyframes = false;
        editorState.applyKeyframes(ShapeKeyframeType.REFRESH_HANDLER, TimelineWindow.getCursorTick());
        vector3$pruneDeletedShapes();
    }

    // Runs after render() has released editorScene, so the scene is read under its own lock.
    @Unique
    private static void vector3$pruneDeletedShapes() {
        Set<String> liveShapeIds = new HashSet<>();
        long stamp = editorState.acquireRead();
        try {
            for (KeyframeTrack track : editorState.getCurrentScene(stamp).keyframeTracks) {
                if (track.keyframeType != ShapeKeyframeType.INSTANCE || !track.enabled) continue;
                for (Keyframe keyframe : track.keyframesByTick.values()) {
                    if (keyframe instanceof ShapeKeyframe shape) liveShapeIds.add(shape.value.shapeId());
                }
            }
        } finally {
            editorState.release(stamp);
        }
        ShapeTrackRegistry.retainOnly(liveShapeIds);
    }

    // Inside Flashback's track popup, just above "Clear keyframes".
    @Inject(method = "renderKeyframeElements", at = @At(value = "CONSTANT", args = "stringValue=flashback.clear_keyframes"))
    private static void vector3$trackGroupItems(float x, float y, int cursorTicks, int middleX, CallbackInfo ci,
            @Local(name = "trackIndex") int trackIndex) {
        List<SelectedKeyframes> targets = vector3$groupTargets(trackIndex);
        String current = vector3$sharedGroup(targets);
        vector3$createGroupItem(trackIndex);
        List<PrefabGroups.Span> spans = PrefabGroups.spans(editorScene);
        if (!spans.isEmpty() && ImGui.beginMenu(I18n.get("vector3.prefab.track.add_to_group"))) {
            for (PrefabGroups.Span span : spans) {
                if (ImGui.menuItem(span.group().name() + "###" + span.group().id(), "", span.group().id().equals(current))) {
                    upgradeToSceneWrite();
                    PrefabGroups.tagSelection(editorScene, targets, span.group().id());
                    editorState.markDirty();
                }
            }
            ImGui.endMenu();
        }
        if (current != null && ImGui.menuItem(I18n.get("vector3.prefab.track.leave_group"))) {
            upgradeToSceneWrite();
            PrefabGroups.tagSelection(editorScene, targets, null);
            editorState.markDirty();
        }
    }

    @Unique
    private static String vector3$sharedGroup(List<SelectedKeyframes> targets) {
        String shared = null;
        for (SelectedKeyframes selected : targets) {
            if (selected.trackIndex() >= editorScene.keyframeTracks.size()) continue;
            TreeMap<Integer, Keyframe> keyframes = editorScene.keyframeTracks.get(selected.trackIndex()).keyframesByTick;
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = keyframes.get(tick);
                String group = keyframe == null ? null : PrefabGroups.groupOf(keyframe);
                if (group == null || (shared != null && !shared.equals(group))) return null;
                shared = group;
            }
        }
        return shared;
    }

    // The add-track menu's check (the first one is the per-track add button): one Skip track at most.
    @WrapOperation(method = "renderKeyframeElements", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/keyframe/KeyframeType;canBeCreatedNormally()Z"))
    private static boolean vector3$singleSkipTrack(KeyframeType<?> type, Operation<Boolean> original) {
        if (type == SkipKeyframeType.INSTANCE && editorScene != null) {
            for (KeyframeTrack track : editorScene.keyframeTracks) {
                if (track.keyframeType == SkipKeyframeType.INSTANCE) return false;
            }
        }
        return original.call(type);
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseClicked(I)Z"))
    private static boolean vector3$keepViewportClicksOutOfTimeline(int button) {
        boolean clicked = vector3$isMouseInTimeline() && ImGui.isMouseClicked(button);
        if (vector3$draggedGroup != null || vector3$trimTrack >= 0) return false;
        // A clip's edge trims it instead of moving it.
        if (clicked && button == 0 && (vector3$hoverEdge == -1 || vector3$hoverEdge == 1)) {
            vector3$beginTrim();
            return false;
        }
        String group = clicked && button <= 1 ? vector3$groupAt(mouseX, mouseY) : null;
        if (group == null) return clicked;
        // Clicks on a group never reach Flashback: left drags the group, right opens its menu.
        if (button == 0 && vector3$draggedGroup == null) {
            vector3$draggedGroup = group;
            vector3$groupDragStartX = mouseX;
            vector3$groupDragStartY = mouseY;
        } else if (button == 1) {
            vector3$menuGroup = group;
            vector3$openGroupMenu = true;
        }
        return false;
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseDragging(I)Z"))
    private static boolean vector3$keepViewportDragOutOfTimeline(int button) {
        if (Vector3.GIZMO_EDITOR.isDragging() || Vector3.ORBIT_GIZMO.isDragging() || Vector3.CAMERA_GIZMO.isDragging() || Vector3.PREFABS.isDragging()
                || vector3$draggedGroup != null) {
            return false;
        }
        return ImGui.isMouseDragging(button);
    }

    @Unique
    // Auto key: a shape picked with nothing selected is edited at the playhead, through a stand-in keyframe
    // holding its interpolated state; the first commit turns it into a real keyframe at the playhead.
    private static ShapeKeyframe vector3$autoKeyframe;
    private static int vector3$autoKeyTrack;
    private static String vector3$autoKeyShape;

    private static void vector3$beginAutoKey(String shapeId) {
        int cursor = TimelineWindow.getCursorTick();
        int trackIndex = -1;
        for (int i = 0; i < editorScene.keyframeTracks.size(); i++) {
            KeyframeTrack track = editorScene.keyframeTracks.get(i);
            if (track.keyframeType == ShapeKeyframeType.INSTANCE && !track.keyframesByTick.isEmpty()
                    && track.keyframesByTick.firstEntry().getValue() instanceof ShapeKeyframe first
                    && first.value.shapeId().equals(shapeId)) {
                trackIndex = i;
                break;
            }
        }
        if (trackIndex < 0) return;
        if (editorScene.keyframeTracks.get(trackIndex).keyframesByTick.containsKey(cursor)) {
            vector3$autoKeyframe = null;
            vector3$selectKeyframe(trackIndex, cursor);
            return;
        }
        ShapeState state = ShapeReparent.stateAt(editorScene, shapeId, cursor);
        if (state == null) return;
        selectedKeyframesList.clear();
        vector3$autoKeyframe = new ShapeKeyframe(state);
        vector3$autoKeyTrack = trackIndex;
        vector3$autoKeyShape = shapeId;
        Vector3.GIZMO_EDITOR.select(vector3$autoKeyframe, TimelineWindowMixin::vector3$commitAutoKey);
    }

    private static void vector3$followPlayhead() {
        if (vector3$autoKeyframe == null || Vector3.GIZMO_EDITOR.isDragging() || editorScene == null) return;
        ShapeState now = ShapeReparent.stateAt(editorScene, vector3$autoKeyShape, TimelineWindow.getCursorTick());
        if (now != null) vector3$autoKeyframe.value = now;
    }

    private static void vector3$commitAutoKey(ShapeState state) {
        int trackIndex = vector3$autoKeyTrack, tick = TimelineWindow.getCursorTick();
        vector3$autoKeyframe = null;
        if (editorScene == null || trackIndex >= editorScene.keyframeTracks.size()) return;
        upgradeToSceneWrite();
        Keyframe existing = editorScene.keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        EditorSceneHistoryAction undo = existing != null
                ? new EditorSceneHistoryAction.SetKeyframe(ShapeKeyframeType.INSTANCE, trackIndex, tick, existing.copy())
                : new EditorSceneHistoryAction.RemoveKeyframe(ShapeKeyframeType.INSTANCE, trackIndex, tick);
        editorScene.push(new EditorSceneHistoryEntry(List.of(undo), List.of(new EditorSceneHistoryAction.SetKeyframe(
                ShapeKeyframeType.INSTANCE, trackIndex, tick, new ShapeKeyframe(state))), I18n.get("vector3.history.auto_key")));
        editorState.markDirty();
        vector3$selectKeyframe(trackIndex, tick);
    }

    private static void vector3$selectKeyframe(int trackIndex, int tick) {
        selectedKeyframesList.clear();
        IntSet ticks = new IntOpenHashSet();
        ticks.add(tick);
        selectedKeyframesList.add(new SelectedKeyframes(ShapeKeyframeType.INSTANCE, trackIndex, ticks));
        editingKeyframeTrack = trackIndex;
        editingKeyframeTick = tick;
        PropertiesWindow.requestFocus();
    }

    // Over the viewport or the Shape Manager, Ctrl+D duplicates the selected shapes and Delete removes them whole;
    // over the timeline, Delete keeps deleting just the selected keyframes.
    @Unique
    private static void vector3$shapeShortcuts() {
        if (ImGui.getIO().getWantTextInput() || selectedKeyframesList.isEmpty()
                || Vector3.GIZMO_EDITOR.isDragging() || Vector3.CAMERA_GIZMO.isDragging()) return;
        if (!ShapeGizmoEditor.mouseInViewport() && !ShapeManagerWindow.isHovered()) return;
        boolean duplicate = ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.D, false);
        boolean delete = ImGui.isKeyPressed(ImGuiKey.Delete, false);
        if (!duplicate && !delete) return;
        Set<String> shapes = ShapeCommands.shapesIn(editorScene, selectedKeyframesList);
        if (shapes.isEmpty()) return;
        upgradeToSceneWrite();
        if (duplicate) {
            ShapeCommands.Duplicate copy = ShapeCommands.duplicate(editorScene, shapes);
            if (copy == null) return;
            editorScene.push(copy.entry());
            selectedKeyframesList.clear();
            selectedKeyframesList.addAll(copy.selection());
        } else {
            EditorSceneHistoryEntry removal = ShapeCommands.delete(editorScene, shapes);
            if (removal == null) return;
            editorScene.push(removal);
            selectedKeyframesList.clear();
            editingKeyframeTrack = -1;
            editingKeyframeTick = -1;
        }
        vector3$keyframesChanged();
    }

    @Unique
    private static void vector3$selectShapeGroup() {
        List<GroupTransform.Member> members = new ArrayList<>();
        for (MultiSelection.Entry entry : MultiSelection.gather(editorScene, selectedKeyframesList,
                editingKeyframeTrack, editingKeyframeTick)) {
            if (entry.original instanceof ShapeKeyframe shape) members.add(new GroupTransform.Member(shape, entry.track, entry.tick));
        }
        if (members.isEmpty()) {
            Vector3.GIZMO_EDITOR.clearSelection();
            return;
        }
        Vector3.GIZMO_EDITOR.selectGroup(members, states -> {
            upgradeToSceneWrite();
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
            editorScene.push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.multi_edit", redo.size())));
            vector3$keyframesChanged();
        });
    }

    private static void vector3$syncGizmoSelection() {
        if (Vector3.GIZMO_EDITOR.isDragging() || Vector3.ORBIT_GIZMO.isDragging() || Vector3.CAMERA_GIZMO.isDragging() || Vector3.PREFABS.isDragging()) {
            return;
        }
        if (vector3$autoKeyframe != null) {
            boolean cancelled = !ShapeManagerWindow.isAutoKey()
                    || ImGui.isKeyPressed(ImGuiKey.Escape) && !ImGui.getIO().getWantTextInput();
            if (!cancelled && selectedKeyframesList.isEmpty()) return;
            vector3$autoKeyframe = null;
        }
        if (MultiSelection.count(selectedKeyframesList) > 1) {
            Vector3.ORBIT_GIZMO.clearSelection();
            Vector3.CAMERA_GIZMO.clearSelection();
            vector3$selectShapeGroup();
            return;
        }
        if (selectedKeyframesList.size() != 1
                || selectedKeyframesList.getFirst().keyframeTicks().size() != 1) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.clearSelection();
            Vector3.CAMERA_GIZMO.clearSelection();
            return;
        }
        SelectedKeyframes selected = selectedKeyframesList.getFirst();
        int trackIndex = selected.trackIndex();
        if (trackIndex < 0 || trackIndex >= editorScene.keyframeTracks.size()) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.clearSelection();
            Vector3.CAMERA_GIZMO.clearSelection();
            return;
        }
        int tick = selected.keyframeTicks().iterator().nextInt();
        Keyframe keyframe = editorScene.keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        if (keyframe instanceof CameraOrbitKeyframe orbitKeyframe) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.CAMERA_GIZMO.clearSelection();
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
        if (keyframe instanceof CameraKeyframe cameraKeyframe) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.CAMERA_GIZMO.select(cameraKeyframe, pose -> {
                CameraKeyframe replacement = (CameraKeyframe) cameraKeyframe.copy();
                replacement.position.set(pose.position());
                replacement.yaw = pose.yaw();
                replacement.pitch = pose.pitch();
                replacement.roll = pose.roll();
                upgradeToSceneWrite();
                editorScene.setKeyframe(trackIndex, tick, replacement);
                EditorStateManager.getCurrent().markDirty();
            });
            return;
        }
        Vector3.CAMERA_GIZMO.clearSelection();
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

    @Unique
    private static boolean vector3$isMouseInTimeline() {
        return !ReplayUI.isMainFrameHovered()
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
