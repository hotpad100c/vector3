package ml.mypals.vectorthree.fb.clips;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Keeps clips from overlapping: the clips being placed stay put and the others are pushed right, as in Resolve. */
public final class ClipOverlap {
    /** {@code keyframe} moves from tick {@code from} to {@code to}. */
    public record Move(int from, int to, Keyframe keyframe) {}

    private ClipOverlap() {}

    private static int length(Keyframe keyframe) {
        return Math.max(1, Math.round(keyframe.getCustomWidthInTicks()));
    }

    /**
     * Where the clips other than {@code anchors} (start ticks) must go so that none overlaps. A clip in the way goes
     * to whichever side of the anchors its middle is on, and pushes the clips behind it along; if going left would
     * pass the start of the timeline it goes right instead.
     */
    public static List<Move> plan(KeyframeTrack track, Set<Integer> anchors) {
        List<int[]> fixed = new ArrayList<>();
        List<Map.Entry<Integer, Keyframe>> others = new ArrayList<>();
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
            if (!(entry.getValue() instanceof ClipKeyframeType.ClipKeyframe)) continue;
            if (anchors.contains(entry.getKey())) {
                int end = entry.getKey() + length(entry.getValue());
                fixed.add(new int[]{entry.getKey(), end});
                low = Math.min(low, entry.getKey());
                high = Math.max(high, end);
            } else {
                others.add(entry);
            }
        }
        List<Move> moves = new ArrayList<>();
        if (fixed.isEmpty()) return moves;
        double middle = (low + high) / 2.0;
        List<Map.Entry<Integer, Keyframe>> left = new ArrayList<>(), right = new ArrayList<>();
        for (Map.Entry<Integer, Keyframe> entry : others) {
            (entry.getKey() + length(entry.getValue()) / 2.0 < middle ? left : right).add(entry);
        }
        List<int[]> obstacles = new ArrayList<>(fixed);
        List<Map.Entry<Integer, Keyframe>> leftovers = new ArrayList<>();
        for (int i = left.size() - 1; i >= 0; i--) {
            Map.Entry<Integer, Keyframe> entry = left.get(i);
            int length = length(entry.getValue()), position = entry.getKey();
            boolean pushed = true;
            while (pushed && position >= 0) {
                pushed = false;
                for (int[] obstacle : obstacles) {
                    if (obstacle[0] < position + length && obstacle[1] > position) {
                        position = obstacle[0] - length;
                        pushed = true;
                    }
                }
            }
            if (position < 0) {
                leftovers.add(entry);
                continue;
            }
            obstacles.add(new int[]{position, position + length});
            if (position != entry.getKey()) moves.add(new Move(entry.getKey(), position, entry.getValue()));
        }
        List<Map.Entry<Integer, Keyframe>> forward = new ArrayList<>(leftovers);
        forward.addAll(right);
        forward.sort(java.util.Comparator.comparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, Keyframe> entry : forward) {
            int length = length(entry.getValue()), position = entry.getKey();
            boolean pushed = true;
            while (pushed) {
                pushed = false;
                for (int[] obstacle : obstacles) {
                    if (obstacle[0] < position + length && obstacle[1] > position) {
                        position = obstacle[1];
                        pushed = true;
                    }
                }
            }
            obstacles.add(new int[]{position, position + length});
            if (position != entry.getKey()) moves.add(new Move(entry.getKey(), position, entry.getValue()));
        }
        return moves;
    }

    /** Applies the plan straight to the track, with no history (for clips that were just added). */
    public static void apply(KeyframeTrack track, Set<Integer> anchors) {
        List<Move> moves = plan(track, anchors);
        for (Move move : moves) track.keyframesByTick.remove(move.from());
        for (Move move : moves) track.keyframesByTick.put(move.to(), move.keyframe());
    }

    /** One history entry that pushes the clips aside, or null when nothing overlaps. */
    public static @Nullable EditorSceneHistoryEntry entry(KeyframeTrack track, int row, Set<Integer> anchors) {
        List<Move> moves = plan(track, anchors);
        if (moves.isEmpty()) return null;
        List<EditorSceneHistoryAction> redo = new ArrayList<>(), undo = new ArrayList<>();
        var type = track.keyframeType;
        for (Move move : moves) redo.add(new EditorSceneHistoryAction.RemoveKeyframe(type, row, move.from()));
        for (Move move : moves) redo.add(new EditorSceneHistoryAction.SetKeyframe(type, row, move.to(), move.keyframe().copy()));
        for (Move move : moves) undo.add(new EditorSceneHistoryAction.RemoveKeyframe(type, row, move.to()));
        for (Move move : moves) undo.add(new EditorSceneHistoryAction.SetKeyframe(type, row, move.from(), move.keyframe().copy()));
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.push_clips", moves.size()));
    }

    /** Start ticks of the clips in {@code ticks} that are on {@code track}. */
    public static Set<Integer> clipsAt(KeyframeTrack track, Iterable<Integer> ticks) {
        Set<Integer> result = new java.util.HashSet<>();
        for (int tick : ticks) if (track.keyframesByTick.get(tick) instanceof ClipKeyframeType.ClipKeyframe) result.add(tick);
        return result;
    }
}
