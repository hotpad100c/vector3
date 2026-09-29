package ml.mypals.vectorthree.fb.timeline;

import ml.mypals.vectorthree.core.clips.AudioEnvelope;
import ml.mypals.vectorthree.fb.clips.ClipOverlap;
import ml.mypals.vectorthree.core.clips.AudioLevel;
import ml.mypals.vectorthree.core.clips.AudioTrim;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.types.AudioKeyframeType;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiMouseCursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.fb.clips.ClipKeyframeType;
import ml.mypals.vectorthree.mixin.flashback.GrabMovementInfoAccessor;
import ml.mypals.vectorthree.fb.clips.ClipProject;
import ml.mypals.vectorthree.fb.clips.ClipsWindow;
import ml.mypals.vectorthree.fb.clips.Trimming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

public final class ClipTimeline {
    private ClipTimeline() {}

    // Every track row is laid out from contentY, so moving it down frees a band above the top track for the Clips track.
    public static float band(float contentY) {
        if (Timeline.scene() == null) return contentY;
        return contentY + ClipProject.updateBand(Timeline.scene(), ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY());
    }

    public static float bandHeight(float totalTrackHeight) {
        return totalTrackHeight + ClipProject.band();
    }

    static boolean clipsDirty;

    /** The last tick any clip reaches; the timeline stretches to it until the clips are composed. */
    public static int end() {
        return clipsEnd;
    }

    /** Both the drag preview and the drop read the grab movement, so a single dragged clip visibly snaps while it moves. */
    public static void snapMovement(Object result) {
        if (Timeline.scene() == null || !(result instanceof GrabMovementInfoAccessor movement)) return;
        int delta = movement.vector3$delta(), snapped = snappedClipDelta(delta, movement.vector3$scalePivot());
        if (snapped != delta) movement.vector3$setDelta(snapped);
    }
    static int clipsEnd;

    // Its start or end snaps onto the nearest other clip's boundary or the playhead.
    public static int snappedClipDelta(int delta, int pivot) {
        if (pivot >= 0 || Timeline.selected().size() != 1) return delta;
        SelectedKeyframes selected = Timeline.selected().getFirst();
        if (selected.keyframeTicks().size() != 1) return delta;
        if (selected.type() != ClipKeyframeType.INSTANCE) {
            int tick = selected.keyframeTicks().iterator().nextInt();
            int threshold = Math.max(1, Timeline.tickAt(12) - Timeline.tickAt(0));
            int start = tick + delta, snapped = nearestBeat(start, tick);
            return snapped != Integer.MIN_VALUE && Math.abs(snapped - start) <= threshold ? Math.max(-tick, snapped - tick) : delta;
        }
        int from = selected.keyframeTicks().iterator().nextInt();
        if (!(Timeline.scene().keyframeTracks.get(selected.trackIndex()).keyframesByTick.get(from)
                instanceof ClipKeyframeType.ClipKeyframe clip)) return delta;
        int threshold = Math.max(1, Timeline.tickAt(12) - Timeline.tickAt(0));
        int start = from + delta, length = clip.value.length();
        int toStart = nearer(ClipProject.snap(Timeline.scene(), start, from), TimelineWindow.getCursorTick(), start) - start;
        int toEnd = nearer(ClipProject.snap(Timeline.scene(), start + length, from), TimelineWindow.getCursorTick(), start + length) - (start + length);
        int nudge = Math.abs(toStart) <= Math.abs(toEnd) ? toStart : toEnd;
        return Math.abs(nudge) <= threshold ? Math.max(-from, delta + nudge) : delta;
    }

    /** The timeline tick of the audio beat nearest {@code tick} (ignoring the audio keyframe at {@code ignore}), or MIN_VALUE. */
    public static int nearestBeat(int tick, int ignore) {
        int best = Integer.MIN_VALUE;
        for (KeyframeTrack track : Timeline.scene().keyframeTracks) {
            if (track.keyframeType != AudioKeyframeType.INSTANCE) continue;
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                if (entry.getKey() == ignore || !(entry.getValue() instanceof AudioEnvelope envelope)) continue;
                AudioTrim trim = (AudioTrim) entry.getValue();
                float pitch = ((AudioLevel) entry.getValue()).vector3$pitch();
                for (int beat : envelope.vector3$beats()) {
                    if (beat < trim.vector3$audioIn() || trim.vector3$audioLength() >= 0
                            && beat > trim.vector3$audioIn() + trim.vector3$audioLength()) continue;
                    int at = entry.getKey() + Math.round((beat - trim.vector3$audioIn()) / pitch);
                    if (best == Integer.MIN_VALUE || Math.abs(at - tick) < Math.abs(best - tick)) best = at;
                }
            }
        }
        return best;
    }

    /** After keyframes are dropped: clips that now overlap the dropped ones are pushed right. */
    public static void pushClipsAside() {
        if (Timeline.scene() == null || trimTrack >= 0) return;
        for (int row = 0; row < Timeline.scene().keyframeTracks.size(); row++) {
            KeyframeTrack track = Timeline.scene().keyframeTracks.get(row);
            if (track.keyframeType != ClipKeyframeType.INSTANCE) continue;
            java.util.Set<Integer> anchors = new java.util.HashSet<>();
            for (SelectedKeyframes selected : Timeline.selected()) {
                if (selected.trackIndex() == row) anchors.addAll(ClipOverlap.clipsAt(track, selected.keyframeTicks()));
            }
            if (anchors.isEmpty()) return;
            var entry = ClipOverlap.entry(track, row, anchors);
            if (entry == null) return;
            Timeline.upgradeToSceneWrite();
            Timeline.scene().push(entry);
            Timeline.keyframesChanged();
            return;
        }
    }

    public static int nearer(int a, int b, int tick) {
        return Math.abs(a - tick) <= Math.abs(b - tick) ? a : b;
    }

    static final int NO_EDGE = 2;
    static int hoverTrack = -1;
    static int hoverTick = -1;
    static int hoverEdge = NO_EDGE;
    static int trimTrack = -1;
    static int trimTick = -1;
    static int trimOriginalTick;
    static boolean trimLeft;
    static Keyframe trimOriginal;
    static Trimming.Range trimRange;
    static float trimStartX;

    public static boolean trimmable(KeyframeTrack track) {
        return track.keyframeType == ClipKeyframeType.INSTANCE || track.keyframeType == AudioKeyframeType.INSTANCE;
    }

    // Clips and audio, as in DaVinci: drag an edge to trim, middle-click to cut in two.
    public static void clipEdges(float x, float rowsY, float lineHeight) {
        hoverTrack = -1;
        hoverTick = -1;
        hoverEdge = NO_EDGE;
        if (trimTrack >= 0) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            updateTrim(x);
            return;
        }
        if (!Timeline.mouseInTimeline()) return;
        for (int row = 0; row < Timeline.scene().keyframeTracks.size() && hoverTrack < 0; row++) {
            KeyframeTrack track = Timeline.scene().keyframeTracks.get(row);
            if (!trimmable(track)) continue;
            float bottom = rowsY + 2 + (row + 1) * lineHeight;
            float top = bottom - lineHeight - (track.keyframeType == ClipKeyframeType.INSTANCE ? ClipProject.band() : 0);
            if (Timeline.mouseY() < top || Timeline.mouseY() > bottom) continue;
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                float width = entry.getValue().getCustomWidthInTicks();
                if (width <= 0 || !Trimming.trimmable(entry.getValue())) continue;
                float left = x + Timeline.xOf(entry.getKey());
                float right = x + Timeline.xOf(entry.getKey() + Math.round(width));
                int edge = Math.abs(Timeline.mouseX() - left) <= 5 ? -1 : Math.abs(Timeline.mouseX() - right) <= 5 ? 1
                        : Timeline.mouseX() > left && Timeline.mouseX() < right ? 0 : NO_EDGE;
                if (edge == NO_EDGE) continue;
                hoverTrack = row;
                hoverTick = entry.getKey();
                hoverEdge = edge;
                if (edge != 0) break;
            }
        }
        if (hoverEdge == -1 || hoverEdge == 1) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        if (hoverTick >= 0 && ImGui.isMouseClicked(2)) {
            split(hoverTrack, hoverTick, Timeline.tickAt(Timeline.mouseX() - x));
        }
    }

    public static void beginTrim() {
        if (hoverTrack < 0 || hoverTrack >= Timeline.scene().keyframeTracks.size()) return;
        Keyframe keyframe = Timeline.scene().keyframeTracks.get(hoverTrack).keyframesByTick.get(hoverTick);
        Trimming.Range range = keyframe == null ? null : Trimming.range(keyframe);
        if (range == null) return;
        trimTrack = hoverTrack;
        trimTick = trimOriginalTick = hoverTick;
        trimLeft = hoverEdge == -1;
        trimOriginal = keyframe;
        trimRange = range;
        trimStartX = Timeline.mouseX();
    }

    /** The start or end of another media keyframe on {@code track} nearest {@code tick}, or {@code tick} itself. */
    public static int snapOnTrack(KeyframeTrack track, int tick, int ignore) {
        int best = track.keyframeType == ClipKeyframeType.INSTANCE ? 0 : tick;
        for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
            float width = entry.getValue().getCustomWidthInTicks();
            if (entry.getKey() == ignore || width <= 0) continue;
            for (int boundary : new int[]{entry.getKey(), entry.getKey() + Math.round(width)}) {
                if (Math.abs(boundary - tick) < Math.abs(best - tick)) best = boundary;
            }
        }
        if (track.keyframeType == AudioKeyframeType.INSTANCE) {
            int beat = nearestBeat(tick, ignore);
            if (beat != Integer.MIN_VALUE && Math.abs(beat - tick) < Math.abs(best - tick)) best = beat;
        }
        return nearer(best, TimelineWindow.getCursorTick(), tick);
    }

    public static void updateTrim(float x) {
        KeyframeTrack track = Timeline.scene().keyframeTracks.get(trimTrack);
        if (!ImGui.isMouseDown(0)) {
            finishTrim(track);
            return;
        }
        Timeline.upgradeToSceneWrite();
        Trimming.Range range = trimRange;
        int delta = Timeline.tickAt(Timeline.mouseX() - x) - Timeline.tickAt(trimStartX - x);
        int threshold = Math.max(1, Timeline.tickAt(12) - Timeline.tickAt(0));
        int from = trimOriginalTick, tick = from, in = range.in(), out = range.out();
        if (trimLeft) {
            int edge = from + delta, snapped = snapOnTrack(track, edge, trimTick);
            if (Math.abs(snapped - edge) <= threshold) edge = snapped;
            in = Math.clamp(range.in() + edge - from, Math.max(0, range.in() - from), range.out() - 1);
            tick = from + in - range.in();
        } else {
            int edge = from + range.length() + delta, snapped = snapOnTrack(track, edge, trimTick);
            if (Math.abs(snapped - edge) <= threshold) edge = snapped;
            out = Math.clamp(range.in() + edge - from, range.in() + 1, range.total());
        }
        if (tick != trimTick && track.keyframesByTick.containsKey(tick)) return;
        track.keyframesByTick.remove(trimTick);
        track.keyframesByTick.put(tick, Trimming.withRange(trimOriginal, in, out));
        trimTick = tick;
        Timeline.state().markDirty();
    }

    // The live edit is put back first so the history entry does the change itself, and undo can take it back.
    public static void finishTrim(KeyframeTrack track) {
        int row = trimTrack, from = trimOriginalTick, to = trimTick;
        Keyframe trimmed = track.keyframesByTick.remove(to);
        Keyframe original = trimOriginal;
        track.keyframesByTick.put(from, original);
        trimTrack = -1;
        trimTick = -1;
        if (trimmed == null || trimmed == original) return;
        Timeline.upgradeToSceneWrite();
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        if (from != to) {
            redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, row, from));
            undo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, row, to));
        }
        redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, to, trimmed.copy()));
        undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, from, original.copy()));
        Timeline.scene().push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.trim_clip")));
        Timeline.keyframesChanged();
        if (track.keyframeType == ClipKeyframeType.INSTANCE) {
            var push = ClipOverlap.entry(track, row, java.util.Set.of(to));
            if (push != null) {
                Timeline.scene().push(push);
                Timeline.keyframesChanged();
            }
        }
    }

    /** The blade: the media keyframe at {@code tick} becomes two meeting at {@code at}. */
    public static void split(int row, int tick, int at) {
        KeyframeTrack track = Timeline.scene().keyframeTracks.get(row);
        Keyframe keyframe = track.keyframesByTick.get(tick);
        Trimming.Range range = keyframe == null ? null : Trimming.range(keyframe);
        if (range == null || at <= tick || at >= tick + range.length()) return;
        int cut = range.in() + at - tick;
        Timeline.upgradeToSceneWrite();
        List<EditorSceneHistoryAction> undo = List.of(
                new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, row, at),
                new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, tick, keyframe.copy()));
        List<EditorSceneHistoryAction> redo = List.of(
                new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, tick, Trimming.withRange(keyframe, range.in(), cut)),
                new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, at, Trimming.withRange(keyframe, cut, range.out())));
        Timeline.scene().push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.split_clip")));
        Timeline.selected().clear();
        Timeline.keyframesChanged();
    }

    public static void handleClips() {
        // Ctrl+B cuts every clip and audio keyframe under the playhead.
        if (!ImGui.getIO().getWantTextInput() && ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.B, false)) {
            int cursor = TimelineWindow.getCursorTick();
            for (int row = 0; row < Timeline.scene().keyframeTracks.size(); row++) {
                KeyframeTrack track = Timeline.scene().keyframeTracks.get(row);
                Map.Entry<Integer, Keyframe> under = trimmable(track) ? track.keyframesByTick.floorEntry(cursor) : null;
                if (under != null) split(row, under.getKey(), cursor);
            }
        }
        if (ImGui.getDragDropPayload(ClipsWindow.PAYLOAD) instanceof String path
                && ImGui.isMouseReleased(0) && Timeline.mouseInTimeline()) {
            Timeline.upgradeToSceneWrite();
            try {
                ClipProject.addClip(Timeline.scene(), java.nio.file.Path.of(path), Timeline.tickAt(Timeline.mouseX() - Timeline.x()));
            } catch (ClipProject.IncompatibleClipException exception) {
                net.minecraft.client.gui.components.toasts.SystemToast.add(Minecraft.getInstance().gui.toastManager(),
                        net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        net.minecraft.network.chat.Component.translatable("vector3.clips.incompatible"),
                        net.minecraft.network.chat.Component.translatable("vector3.clips.incompatible_detail",
                                exception.clipVersion, exception.projectVersion));
            } catch (java.io.IOException exception) {
                Mod.LOGGER.warn("Could not add clip {}", path, exception);
            }
            // The Clips track may have been inserted at the top, which moves every other track down.
            Timeline.selected().clear();
            Timeline.setEditingTrack(-1);
            Timeline.setEditingTick(-1);
            Timeline.keyframesChanged();
        }
        clipsDirty = ClipProject.dirty(Timeline.scene());
        clipsEnd = ClipProject.end(Timeline.scene());
        if (ClipsWindow.consumeApplyRequest() && clipsDirty) {
            Timeline.upgradeToSceneWrite();
            try {
                ClipProject.apply(Timeline.state(), Timeline.scene());
            } catch (java.io.IOException exception) {
                Mod.LOGGER.warn("Could not apply the clip changes", exception);
            }
        }
    }

    public static int belowClips(int index) {
        List<KeyframeTrack> tracks = Timeline.scene().keyframeTracks;
        return !tracks.isEmpty() && tracks.getFirst().keyframeType == ClipKeyframeType.INSTANCE ? index - 1 : index;
    }
}
