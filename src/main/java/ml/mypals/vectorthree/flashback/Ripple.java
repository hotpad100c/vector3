package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public final class Ripple {
    public record Result(@Nullable EditorSceneHistoryEntry entry, @Nullable String problem) {}

    private record Move(KeyframeTrack track, int row, int tick, Keyframe keyframe) {}

    private Ripple() {}

    public static Result delete(EditorScene scene, List<SelectedKeyframes> selection) {
        int start = Integer.MAX_VALUE, end = Integer.MIN_VALUE;
        List<Move> removed = new ArrayList<>();
        Set<Long> removedKeys = new HashSet<>();
        for (SelectedKeyframes selected : selection) {
            if (selected.trackIndex() >= scene.keyframeTracks.size()) continue;
            KeyframeTrack track = scene.keyframeTracks.get(selected.trackIndex());
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = track.keyframesByTick.get(tick);
                if (keyframe == null) continue;
                removed.add(new Move(track, selected.trackIndex(), tick, keyframe));
                removedKeys.add(key(selected.trackIndex(), tick));
                start = Math.min(start, tick);
                end = Math.max(end, tick + Math.max(0, Math.round(keyframe.getCustomWidthInTicks())));
            }
        }
        if (removed.isEmpty()) return new Result(null, "vector3.ripple.nothing");
        if (end <= start) {
            Integer next = null;
            for (Move move : removed) {
                Integer after = move.track().keyframesByTick.higherKey(end);
                if (after != null && (next == null || after < next)) next = after;
            }
            if (next == null) return new Result(null, "vector3.ripple.nothing");
            end = next;
        }
        int shift = end - start;

        List<Move> moved = new ArrayList<>();
        for (int row = 0; row < scene.keyframeTracks.size(); row++) {
            KeyframeTrack track = scene.keyframeTracks.get(row);
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.tailMap(end, true).entrySet()) {
                if (!removedKeys.contains(key(row, entry.getKey()))) moved.add(new Move(track, row, entry.getKey(), entry.getValue()));
            }
        }
        Set<Long> movedKeys = new HashSet<>();
        moved.forEach(move -> movedKeys.add(key(move.row(), move.tick())));
        for (Move move : moved) {
            int to = move.tick() - shift;
            long target = key(move.row(), to);
            if (move.track().keyframesByTick.containsKey(to) && !removedKeys.contains(target) && !movedKeys.contains(target)) {
                return new Result(null, "vector3.ripple.overlap");
            }
        }
        moved.sort(Comparator.comparingInt(Move::tick));

        List<EditorSceneHistoryAction> redo = new ArrayList<>(), undo = new ArrayList<>();
        for (Move move : removed) {
            redo.add(new EditorSceneHistoryAction.RemoveKeyframe(move.track().keyframeType, move.row(), move.tick()));
        }
        for (Move move : moved) {
            redo.add(new EditorSceneHistoryAction.RemoveKeyframe(move.track().keyframeType, move.row(), move.tick()));
            redo.add(new EditorSceneHistoryAction.SetKeyframe(move.track().keyframeType, move.row(), move.tick() - shift, move.keyframe().copy()));
        }
        List<Move> backwards = new ArrayList<>(moved);
        Collections.reverse(backwards);
        for (Move move : backwards) {
            undo.add(new EditorSceneHistoryAction.RemoveKeyframe(move.track().keyframeType, move.row(), move.tick() - shift));
            undo.add(new EditorSceneHistoryAction.SetKeyframe(move.track().keyframeType, move.row(), move.tick(), move.keyframe().copy()));
        }
        for (Move move : removed) {
            undo.add(new EditorSceneHistoryAction.SetKeyframe(move.track().keyframeType, move.row(), move.tick(), move.keyframe().copy()));
        }
        return new Result(new EditorSceneHistoryEntry(undo, redo,
                I18n.get("vector3.history.ripple_delete", removed.size(), shift)), null);
    }

    private static long key(int row, int tick) {
        return (long) row << 32 | (tick & 0xFFFFFFFFL);
    }
}
