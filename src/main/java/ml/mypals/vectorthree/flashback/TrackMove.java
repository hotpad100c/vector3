package ml.mypals.vectorthree.flashback;

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
    public record Plan(Map<Integer, Integer> targets, int trackCount) {
        public boolean creates(int target) {
            return target >= trackCount;
        }
    }

    private TrackMove() {}

    public static @Nullable Plan plan(EditorScene scene, List<SelectedKeyframes> selection, int rowDelta) {
        if (rowDelta == 0 || selection.isEmpty()) return null;
        int count = scene.keyframeTracks.size();
        Map<Integer, Integer> raw = new LinkedHashMap<>();
        for (SelectedKeyframes selected : selection) {
            int source = selected.trackIndex(), target = source + rowDelta;
            if (source < 0 || source >= count || target < 0) return null;
            KeyframeType<?> type = scene.keyframeTracks.get(source).keyframeType;
            if (target < count) {
                if (scene.keyframeTracks.get(target).keyframeType != type) return null;
            } else if (type == SkipKeyframeType.INSTANCE) {
                return null;
            }
            raw.put(source, target);
        }
        TreeSet<Integer> created = new TreeSet<>();
        raw.values().forEach(target -> { if (target >= count) created.add(target); });
        Map<Integer, Integer> compact = new LinkedHashMap<>();
        List<Integer> order = new ArrayList<>(created);
        raw.forEach((source, target) -> compact.put(source, target >= count ? count + order.indexOf(target) : target));
        return new Plan(compact, count);
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

    private static long key(int track, int tick) {
        return ((long) track << 32) | (tick & 0xFFFFFFFFL);
    }
}
