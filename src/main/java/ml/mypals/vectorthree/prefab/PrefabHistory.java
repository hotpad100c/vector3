package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * History actions only rebuild tracks and keyframes, so a track an undo or redo brings back loses its
 * name and colour. Each push remembers those for the tracks it removes and adds; undo puts back
 * the removed ones, redo the added ones. Tracks the entry left alone keep their own fields untouched.
 */
public final class PrefabHistory {
    private record Meta(@Nullable String name, int colour) {
        static Meta of(KeyframeTrack track) {
            return new Meta(track.customName, track.customColour);
        }

        void applyTo(KeyframeTrack track) {
            track.customName = name;
            track.customColour = colour;
        }
    }

    private record Change(Map<Integer, Meta> removed, Map<Integer, Meta> added) {}

    private static final Map<EditorSceneHistoryEntry, Change> CHANGES = new IdentityHashMap<>();
    private static @Nullable List<KeyframeTrack> beforePush;

    private PrefabHistory() {}

    public static void beforePush(EditorScene scene) {
        beforePush = new ArrayList<>(scene.keyframeTracks);
    }

    public static void afterPush(EditorScene scene, EditorSceneHistoryEntry entry) {
        List<KeyframeTrack> before = beforePush;
        beforePush = null;
        if (before == null) return;
        Set<KeyframeTrack> now = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        now.addAll(scene.keyframeTracks);
        Set<KeyframeTrack> old = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        old.addAll(before);
        Map<Integer, Meta> removed = new LinkedHashMap<>();
        for (int i = 0; i < before.size(); i++) {
            if (!now.contains(before.get(i))) removed.put(i, Meta.of(before.get(i)));
        }
        Map<Integer, Meta> added = new LinkedHashMap<>();
        for (int i = 0; i < scene.keyframeTracks.size(); i++) {
            if (!old.contains(scene.keyframeTracks.get(i))) added.put(i, Meta.of(scene.keyframeTracks.get(i)));
        }
        if (!removed.isEmpty() || !added.isEmpty()) CHANGES.put(entry, new Change(removed, added));
    }

    /** Re-reads the added tracks' fields, for callers that name and tag new tracks after pushing. */
    public static void refreshAdded(EditorScene scene, EditorSceneHistoryEntry entry) {
        Change change = CHANGES.get(entry);
        if (change == null) return;
        change.added().replaceAll((index, meta) -> index < scene.keyframeTracks.size()
                ? Meta.of(scene.keyframeTracks.get(index)) : meta);
    }

    public static void undone(EditorScene scene, EditorSceneHistoryEntry entry) {
        Change change = CHANGES.get(entry);
        if (change != null) apply(scene, change.removed());
    }

    public static void redone(EditorScene scene, EditorSceneHistoryEntry entry) {
        Change change = CHANGES.get(entry);
        if (change != null) apply(scene, change.added());
    }

    private static void apply(EditorScene scene, Map<Integer, Meta> metas) {
        metas.forEach((index, meta) -> {
            if (index < scene.keyframeTracks.size()) meta.applyTo(scene.keyframeTracks.get(index));
        });
    }
}
