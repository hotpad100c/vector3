package ml.mypals.vectorthree.flashback;

import ml.mypals.vectorthree.prefab.PrefabGroups;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import ml.mypals.vectorthree.flashback.skip.SkipKeyframeType;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;

public final class TrackMove {
    public record Plan(Map<Integer, Integer> targets, int trackCount, boolean valid) {
        public boolean creates(int target) {
            return target >= trackCount;
        }
    }

    private TrackMove() {}

    // An invalid plan still carries its targets, so the drop preview can show where it would land.
    public static @Nullable Plan plan(EditorScene scene, List<SelectedKeyframes> selection, int rowDelta,
            @Nullable IntUnaryOperator retime) {
        if (rowDelta == 0 || selection.isEmpty()) return null;
        int count = scene.keyframeTracks.size();
        Set<Long> moving = new HashSet<>();
        for (SelectedKeyframes selected : selection) {
            for (int tick : selected.keyframeTicks()) moving.add(key(selected.trackIndex(), tick));
        }
        boolean valid = true;
        Map<Integer, Integer> raw = new LinkedHashMap<>();
        for (SelectedKeyframes selected : selection) {
            int source = selected.trackIndex(), target = source + rowDelta;
            if (source < 0 || source >= count) return null;
            KeyframeType<?> type = scene.keyframeTracks.get(source).keyframeType;
            if (target < 0) {
                valid = false;
            } else if (target < count) {
                KeyframeTrack targetTrack = scene.keyframeTracks.get(target);
                if (targetTrack.keyframeType != type) {
                    valid = false;
                } else if (retime != null) {
                    for (int tick : selected.keyframeTicks()) {
                        int to = retime.applyAsInt(tick);
                        if (targetTrack.keyframesByTick.containsKey(to) && !moving.contains(key(target, to))) valid = false;
                    }
                }
            } else if (type == SkipKeyframeType.INSTANCE) {
                valid = false;
            }
            raw.put(source, target);
        }
        TreeSet<Integer> created = new TreeSet<>();
        raw.values().forEach(target -> { if (target >= count) created.add(target); });
        Map<Integer, Integer> compact = new LinkedHashMap<>();
        List<Integer> order = new ArrayList<>(created);
        raw.forEach((source, target) -> compact.put(source, target >= count ? count + order.indexOf(target) : target));
        return new Plan(compact, count, valid);
    }

    public static EditorSceneHistoryEntry entry(EditorScene scene, List<SelectedKeyframes> selection, Plan plan,
            IntUnaryOperator retime, List<SelectedKeyframes> movedSelection) {
        List<EditorSceneHistoryAction> redo = new ArrayList<>(), undo = new ArrayList<>();
        List<EditorSceneHistoryAction> redoSets = new ArrayList<>(), undoSets = new ArrayList<>();
        List<EditorSceneHistoryAction> undoCovered = new ArrayList<>();
        TreeSet<Integer> newTracks = new TreeSet<>();
        Set<Long> moving = new HashSet<>();
        for (SelectedKeyframes selected : selection) {
            for (int tick : selected.keyframeTicks()) moving.add(key(selected.trackIndex(), tick));
        }
        for (SelectedKeyframes selected : selection) {
            int source = selected.trackIndex(), target = plan.targets().get(source);
            KeyframeTrack sourceTrack = scene.keyframeTracks.get(source);
            KeyframeType<?> type = sourceTrack.keyframeType;
            KeyframeTrack targetTrack = plan.creates(target) ? null : scene.keyframeTracks.get(target);
            if (targetTrack == null) newTracks.add(target);
            IntSet movedTicks = new IntOpenHashSet();
            for (int from : selected.keyframeTicks()) {
                Keyframe keyframe = sourceTrack.keyframesByTick.get(from);
                if (keyframe == null) continue;
                int to = retime.applyAsInt(from);
                movedTicks.add(to);
                redo.add(new EditorSceneHistoryAction.RemoveKeyframe(type, source, from));
                redoSets.add(new EditorSceneHistoryAction.SetKeyframe(type, target, to, keyframe.copy()));
                undo.add(new EditorSceneHistoryAction.RemoveKeyframe(type, target, to));
                undoSets.add(new EditorSceneHistoryAction.SetKeyframe(type, source, from, keyframe.copy()));
                Keyframe covered = targetTrack == null ? null : targetTrack.keyframesByTick.get(to);
                if (covered != null && !moving.contains(key(target, to))) {
                    undoCovered.add(new EditorSceneHistoryAction.SetKeyframe(type, target, to, covered.copy()));
                }
            }
            movedSelection.add(new SelectedKeyframes(type, target, movedTicks));
        }
        List<EditorSceneHistoryAction> fullRedo = new ArrayList<>();
        for (int index : newTracks) {
            int source = sourceOf(plan, index);
            fullRedo.add(new EditorSceneHistoryAction.AddTrack(scene.keyframeTracks.get(source).keyframeType, index));
        }
        fullRedo.addAll(redo);
        fullRedo.addAll(redoSets);
        undo.addAll(undoSets);
        undo.addAll(undoCovered);
        for (int index : newTracks.descendingSet()) {
            int source = sourceOf(plan, index);
            undo.add(new EditorSceneHistoryAction.RemoveTrack(scene.keyframeTracks.get(source).keyframeType, index));
        }
        return new EditorSceneHistoryEntry(undo, fullRedo, I18n.get("vector3.history.move_to_track"));
    }

    private static int sourceOf(Plan plan, int target) {
        for (Map.Entry<Integer, Integer> entry : plan.targets().entrySet()) {
            if (entry.getValue() == target) return entry.getKey();
        }
        throw new IllegalStateException("No source for track " + target);
    }

    /** Every source row onto itself: an Alt-drag that stays on its tracks. */
    public static Plan inPlace(EditorScene scene, List<SelectedKeyframes> selection) {
        Map<Integer, Integer> targets = new LinkedHashMap<>();
        for (SelectedKeyframes selected : selection) targets.put(selected.trackIndex(), selected.trackIndex());
        return new Plan(targets, scene.keyframeTracks.size(), true);
    }

    /**
     * Like {@link #entry} but the originals stay: copies land where the drag would have put them, tagged with
     * {@code group} (null leaves them ungrouped). Returns null when every copy would sit on its own original.
     */
    public static @Nullable EditorSceneHistoryEntry copyEntry(EditorScene scene, List<SelectedKeyframes> selection, Plan plan,
            IntUnaryOperator retime, @Nullable String group, List<SelectedKeyframes> copiedSelection) {
        List<EditorSceneHistoryAction> redo = new ArrayList<>(), undo = new ArrayList<>(), undoCovered = new ArrayList<>();
        TreeSet<Integer> newTracks = new TreeSet<>();
        for (SelectedKeyframes selected : selection) {
            int source = selected.trackIndex(), target = plan.targets().get(source);
            KeyframeTrack sourceTrack = scene.keyframeTracks.get(source);
            KeyframeTrack targetTrack = plan.creates(target) ? null : scene.keyframeTracks.get(target);
            if (targetTrack == null) newTracks.add(target);
            IntSet copiedTicks = new IntOpenHashSet();
            for (int from : selected.keyframeTicks()) {
                Keyframe keyframe = sourceTrack.keyframesByTick.get(from);
                int to = retime.applyAsInt(from);
                if (keyframe == null || target == source && to == from) continue;
                Keyframe copy = keyframe.copy();
                PrefabGroups.tag(copy, group);
                redo.add(new EditorSceneHistoryAction.SetKeyframe(sourceTrack.keyframeType, target, to, copy));
                undo.add(new EditorSceneHistoryAction.RemoveKeyframe(sourceTrack.keyframeType, target, to));
                Keyframe covered = targetTrack == null ? null : targetTrack.keyframesByTick.get(to);
                if (covered != null) {
                    undoCovered.add(new EditorSceneHistoryAction.SetKeyframe(sourceTrack.keyframeType, target, to, covered.copy()));
                }
                copiedTicks.add(to);
            }
            if (!copiedTicks.isEmpty()) copiedSelection.add(new SelectedKeyframes(sourceTrack.keyframeType, target, copiedTicks));
        }
        if (redo.isEmpty()) return null;
        List<EditorSceneHistoryAction> fullRedo = new ArrayList<>();
        for (int index : newTracks) {
            fullRedo.add(new EditorSceneHistoryAction.AddTrack(scene.keyframeTracks.get(sourceOf(plan, index)).keyframeType, index));
        }
        fullRedo.addAll(redo);
        undo.addAll(undoCovered);
        for (int index : newTracks.descendingSet()) {
            undo.add(new EditorSceneHistoryAction.RemoveTrack(scene.keyframeTracks.get(sourceOf(plan, index)).keyframeType, index));
        }
        return new EditorSceneHistoryEntry(undo, fullRedo, I18n.get("vector3.history.copy_keyframes", redo.size()));
    }

    /** A copy of one whole track inserted at its own index, so the original can be dragged away from it. */
    public static EditorSceneHistoryEntry copyTrack(EditorScene scene, int index) {
        KeyframeTrack track = scene.keyframeTracks.get(index);
        List<EditorSceneHistoryAction> redo = new ArrayList<>();
        redo.add(new EditorSceneHistoryAction.AddTrack(track.keyframeType, index));
        for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
            Keyframe copy = entry.getValue().copy();
            PrefabGroups.tag(copy, null);
            redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, entry.getKey(), copy));
        }
        return new EditorSceneHistoryEntry(List.of(new EditorSceneHistoryAction.RemoveTrack(track.keyframeType, index)),
                redo, I18n.get("vector3.history.copy_track"));
    }

    private static long key(int track, int tick) {
        return ((long) track << 32) | (tick & 0xFFFFFFFFL);
    }
}
