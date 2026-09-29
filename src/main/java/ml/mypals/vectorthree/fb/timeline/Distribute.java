package ml.mypals.vectorthree.fb.timeline;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Spreads the selected keyframes evenly over a number of ticks, starting at the first one. Keyframes are placed by
 * their distinct ticks, so keyframes lined up on one tick (on different tracks) move together and stay lined up.
 */
public final class Distribute {
    /** {@code moved} maps each old tick to its new one. */
    public record Result(@Nullable EditorSceneHistoryEntry entry, List<SelectedKeyframes> selection,
            Map<Integer, Integer> moved, @Nullable String problem) {
        static Result failed(List<SelectedKeyframes> selection, String problem) {
            return new Result(null, selection, Map.of(), problem);
        }
    }

    private record Move(KeyframeTrack track, int row, int from, int to, Keyframe keyframe) {}

    private Distribute() {}

    /** The distinct ticks of the selection, in order. */
    public static TreeSet<Integer> ticks(EditorScene scene, List<SelectedKeyframes> selection) {
        TreeSet<Integer> ticks = new TreeSet<>();
        for (SelectedKeyframes selected : selection) {
            if (selected.trackIndex() >= scene.keyframeTracks.size()) continue;
            KeyframeTrack track = scene.keyframeTracks.get(selected.trackIndex());
            for (int tick : selected.keyframeTicks()) {
                if (track.keyframesByTick.containsKey(tick)) ticks.add(tick);
            }
        }
        return ticks;
    }

    public static Result apply(EditorScene scene, List<SelectedKeyframes> selection, int span) {
        TreeSet<Integer> ticks = ticks(scene, selection);
        if (ticks.size() < 2) return Result.failed(selection, "vector3.distribute.too_few");
        if (span < ticks.size() - 1) return Result.failed(selection, "vector3.distribute.too_short");
        int first = ticks.first(), count = ticks.size();
        Map<Integer, Integer> target = new HashMap<>();
        int index = 0;
        for (int tick : ticks) target.put(tick, first + (int) Math.round((double) span * index++ / (count - 1)));

        List<Move> moves = new ArrayList<>();
        Set<Long> moving = new HashSet<>();
        for (SelectedKeyframes selected : selection) {
            if (selected.trackIndex() >= scene.keyframeTracks.size()) continue;
            KeyframeTrack track = scene.keyframeTracks.get(selected.trackIndex());
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = track.keyframesByTick.get(tick);
                if (keyframe == null) continue;
                moves.add(new Move(track, selected.trackIndex(), tick, target.get(tick), keyframe));
                moving.add(key(selected.trackIndex(), tick));
            }
        }
        for (Move move : moves) {
            if (move.to() != move.from() && move.track().keyframesByTick.containsKey(move.to())
                    && !moving.contains(key(move.row(), move.to()))) {
                return Result.failed(selection, "vector3.distribute.overlap");
            }
        }

        // Everything moving comes off first, so keyframes can pass over each other.
        List<EditorSceneHistoryAction> redo = new ArrayList<>(), undo = new ArrayList<>();
        for (Move move : moves) {
            redo.add(new EditorSceneHistoryAction.RemoveKeyframe(move.track().keyframeType, move.row(), move.from()));
            undo.add(new EditorSceneHistoryAction.RemoveKeyframe(move.track().keyframeType, move.row(), move.to()));
        }
        for (Move move : moves) {
            redo.add(new EditorSceneHistoryAction.SetKeyframe(move.track().keyframeType, move.row(), move.to(), move.keyframe().copy()));
            undo.add(new EditorSceneHistoryAction.SetKeyframe(move.track().keyframeType, move.row(), move.from(), move.keyframe().copy()));
        }

        List<SelectedKeyframes> moved = new ArrayList<>();
        for (SelectedKeyframes selected : selection) {
            IntSet next = new IntOpenHashSet();
            for (int tick : selected.keyframeTicks()) next.add(target.getOrDefault(tick, tick));
            moved.add(new SelectedKeyframes(selected.type(), selected.trackIndex(), next));
        }
        return new Result(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.distribute", moves.size(), span)),
                moved, target, null);
    }

    private static long key(int row, int tick) {
        return (long) row << 32 | (tick & 0xFFFFFFFFL);
    }
}
