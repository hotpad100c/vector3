package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.clips.ClipKeyframeType;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tracks selected in the timeline's track list, held by identity so reordering keeps them. */
public final class TrackSelection {
    private static final Set<KeyframeTrack> selected = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<KeyframeTrack> boxBase = Collections.newSetFromMap(new IdentityHashMap<>());
    private static @Nullable KeyframeTrack anchor;

    private TrackSelection() {}

    public static boolean contains(KeyframeTrack track) {
        return selected.contains(track);
    }

    public static boolean isEmpty() {
        return selected.isEmpty();
    }

    public static int size() {
        return selected.size();
    }

    public static void clear() {
        selected.clear();
        anchor = null;
    }

    public static void prune(EditorScene scene) {
        selected.removeIf(track -> !scene.keyframeTracks.contains(track));
        if (anchor != null && !scene.keyframeTracks.contains(anchor)) anchor = null;
    }

    public static void only(KeyframeTrack track) {
        selected.clear();
        selected.add(track);
        anchor = track;
    }

    /** Ctrl toggles, Shift extends from the last clicked track, a plain click selects just this one. */
    public static void click(List<KeyframeTrack> tracks, @Nullable KeyframeTrack track, boolean ctrl, boolean shift) {
        if (track == null) {
            if (!ctrl && !shift) clear();
            return;
        }
        if (shift && anchor != null && tracks.contains(anchor)) {
            int from = tracks.indexOf(anchor), to = tracks.indexOf(track);
            if (!ctrl) selected.clear();
            selected.addAll(tracks.subList(Math.min(from, to), Math.max(from, to) + 1));
            return;
        }
        if (ctrl) {
            if (!selected.remove(track)) selected.add(track);
            anchor = track;
            return;
        }
        only(track);
    }

    public static void beginBox(boolean additive) {
        boxBase.clear();
        if (additive) boxBase.addAll(selected);
    }

    public static void box(Collection<KeyframeTrack> tracks) {
        selected.clear();
        selected.addAll(boxBase);
        selected.addAll(tracks);
    }

    /** Moves every selected track one row up ({@code -1}) or down, keeping their spacing; false at the edge. The Clips track stays on top. */
    public static boolean move(EditorScene scene, int direction, float lineHeight) {
        List<KeyframeTrack> tracks = scene.keyframeTracks;
        int count = tracks.size(), floor = count > 0 && tracks.getFirst().keyframeType == ClipKeyframeType.INSTANCE ? 1 : 0;
        if (tracks.subList(floor, count).stream().noneMatch(TrackSelection::movable)) return false;
        if (count - floor < 2 || movable(tracks.get(direction < 0 ? floor : count - 1))) return false;
        if (direction < 0) {
            for (int i = floor + 1; i < count; i++) swapIfSelected(tracks, i, i - 1, direction, lineHeight);
        } else {
            for (int i = count - 2; i >= floor; i--) swapIfSelected(tracks, i, i + 1, direction, lineHeight);
        }
        return true;
    }

    private static boolean movable(KeyframeTrack track) {
        return selected.contains(track) && track.keyframeType != ClipKeyframeType.INSTANCE;
    }

    private static void swapIfSelected(List<KeyframeTrack> tracks, int index, int target, int direction, float lineHeight) {
        KeyframeTrack moving = tracks.get(index), other = tracks.get(target);
        if (!movable(moving) || movable(other)) return;
        Collections.swap(tracks, index, target);
        moving.animatedOffsetInUi -= direction * lineHeight;
        other.animatedOffsetInUi += direction * lineHeight;
    }

    public static @Nullable EditorSceneHistoryEntry delete(EditorScene scene) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < scene.keyframeTracks.size(); i++) {
            if (selected.contains(scene.keyframeTracks.get(i))) indices.add(i);
        }
        if (indices.isEmpty()) return null;
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        for (int i = indices.size() - 1; i >= 0; i--) {
            int index = indices.get(i);
            redo.add(new EditorSceneHistoryAction.RemoveTrack(scene.keyframeTracks.get(index).keyframeType, index));
        }
        for (int index : indices) {
            KeyframeTrack track = scene.keyframeTracks.get(index);
            undo.add(new EditorSceneHistoryAction.AddTrack(track.keyframeType, index));
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, entry.getKey(), entry.getValue().copy()));
            }
        }
        clear();
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.delete_tracks", indices.size()));
    }
}
