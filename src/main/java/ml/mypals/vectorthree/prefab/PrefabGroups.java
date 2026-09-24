package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.resources.language.I18n;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class PrefabGroups {
  
    public record Span(PrefabGroup group, List<Integer> tracks, int firstTick, int lastTick) {}

    /** A clickable part of a group on the timeline; {@code row} is -1 for the bar, else a track row. */
    public record Handle(String groupId, float left, float top, float right, float bottom, int row) {}


    public static final class Drag {
        private final String groupId;
        private final Map<Integer, TreeMap<Integer, Keyframe>> originals = new LinkedHashMap<>();
        private final int firstTick;
        private int applied;

        public Drag(EditorScene scene, Span span) {
            groupId = span.group().id();
            firstTick = span.firstTick();
            for (int index : span.tracks()) originals.put(index, scene.keyframeTracks.get(index).keyframesByTick);
        }

        public boolean update(EditorScene scene, int delta) {
            delta = Math.max(delta, -firstTick);
            if (delta == applied || !valid(scene)) return false;
            for (Map.Entry<Integer, TreeMap<Integer, Keyframe>> entry : originals.entrySet()) {
                TreeMap<Integer, Keyframe> shifted = new TreeMap<>();
                int offset = delta;
                entry.getValue().forEach((tick, keyframe) -> shifted.put(tick + offset, keyframe));
                scene.keyframeTracks.get(entry.getKey()).keyframesByTick = shifted;
            }
            applied = delta;
            return true;
        }

        public @Nullable EditorSceneHistoryEntry finish(EditorScene scene) {
            if (!valid(scene)) return null;
            originals.forEach((index, keyframes) -> scene.keyframeTracks.get(index).keyframesByTick = keyframes);
            if (applied == 0) return null;
            for (Span span : spans(scene)) {
                if (span.group().id().equals(groupId)) return move(scene, span, applied);
            }
            return null;
        }

        public void cancel(EditorScene scene) {
            if (valid(scene)) originals.forEach((index, keyframes) -> scene.keyframeTracks.get(index).keyframesByTick = keyframes);
            applied = 0;
        }

        private boolean valid(EditorScene scene) {
            for (int index : originals.keySet()) {
                if (index >= scene.keyframeTracks.size() || !groupId.equals(groupOf(scene.keyframeTracks.get(index)))) return false;
            }
            return true;
        }
    }

    private record OldTrack(int index, KeyframeType<?> type, String name, int colour, TreeMap<Integer, Keyframe> keyframes) {}

    private PrefabGroups() {}

    public static Map<String, PrefabGroup> groups(EditorScene scene) {
        return ((PrefabGroupHolder.Scene) scene).vector3$prefabGroups().groups;
    }

    public static @Nullable String groupOf(KeyframeTrack track) {
        return ((PrefabGroupHolder.Track) track).vector3$prefabGroup();
    }

    public static void tag(KeyframeTrack track, @Nullable String id) {
        ((PrefabGroupHolder.Track) track).vector3$setPrefabGroup(id);
    }

    public static List<Span> spans(EditorScene scene) {
        Map<String, List<Integer>> tracks = new LinkedHashMap<>();
        for (int i = 0; i < scene.keyframeTracks.size(); i++) {
            String id = groupOf(scene.keyframeTracks.get(i));
            if (id != null && groups(scene).containsKey(id)) tracks.computeIfAbsent(id, key -> new ArrayList<>()).add(i);
        }
        List<Span> spans = new ArrayList<>();
        tracks.forEach((id, indices) -> {
            int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
            for (int index : indices) {
                TreeMap<Integer, Keyframe> keyframes = scene.keyframeTracks.get(index).keyframesByTick;
                if (keyframes.isEmpty()) continue;
                first = Math.min(first, keyframes.firstKey());
                last = Math.max(last, keyframes.lastKey());
            }
            if (first <= last) spans.add(new Span(groups(scene).get(id), indices, first, last));
        });
        return spans;
    }

    public static EditorSceneHistoryEntry move(EditorScene scene, Span span, int delta) {
        delta = Math.max(delta, -span.firstTick());
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        List<EditorSceneHistoryAction> undoSets = new ArrayList<>(), redoSets = new ArrayList<>();
        for (int index : span.tracks()) {
            KeyframeTrack track = scene.keyframeTracks.get(index);
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                int from = entry.getKey(), to = from + delta;
                redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, index, from));
                redoSets.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, to, entry.getValue().copy()));
                undo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, index, to));
                undoSets.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, from, entry.getValue().copy()));
            }
        }
        // Every remove before any set, so keyframes moving onto each other's old ticks survive.
        redo.addAll(redoSets);
        undo.addAll(undoSets);
        groups(scene).put(span.group().id(), span.group().withStartTick(span.group().startTick() + delta));
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.prefab.history.move", span.group().name()));
    }

    public static EditorSceneHistoryEntry delete(EditorScene scene, Span span) {
        List<OldTrack> old = snapshot(scene, span);
        List<EditorSceneHistoryAction> redo = new ArrayList<>();
        for (int i = old.size() - 1; i >= 0; i--) redo.add(new EditorSceneHistoryAction.RemoveTrack(old.get(i).type(), old.get(i).index()));
        return new EditorSceneHistoryEntry(restore(old), redo, I18n.get("vector3.prefab.history.delete", span.group().name()));
    }

   
    public static EditorSceneHistoryEntry replace(EditorScene scene, Span span, Prefab world, int startTick,
            float timeScale, List<Integer> newIndices) {
        List<OldTrack> old = snapshot(scene, span);
        int base = span.tracks().getFirst();
        List<EditorSceneHistoryAction> redo = new ArrayList<>();
        for (int i = old.size() - 1; i >= 0; i--) redo.add(new EditorSceneHistoryAction.RemoveTrack(old.get(i).type(), old.get(i).index()));
        redo.addAll(add(world, base, startTick, timeScale));
        List<EditorSceneHistoryAction> undo = new ArrayList<>();
        for (int i = world.tracks().size() - 1; i >= 0; i--) {
            undo.add(new EditorSceneHistoryAction.RemoveTrack(world.tracks().get(i).type(), base + i));
        }
        undo.addAll(restore(old));
        for (int i = 0; i < world.tracks().size(); i++) newIndices.add(base + i);
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.prefab.history.edit", span.group().name()));
    }

    static List<EditorSceneHistoryAction> add(Prefab world, int base, int startTick, float timeScale) {
        List<EditorSceneHistoryAction> actions = new ArrayList<>();
        for (int i = 0; i < world.tracks().size(); i++) {
            Prefab.Track track = world.tracks().get(i);
            actions.add(new EditorSceneHistoryAction.AddTrack(track.type(), base + i));
            for (Map.Entry<Integer, Keyframe> entry : track.keyframes().entrySet()) {
                int tick = Math.max(0, startTick + Math.round(entry.getKey() * timeScale));
                actions.add(new EditorSceneHistoryAction.SetKeyframe(track.type(), base + i, tick, entry.getValue()));
            }
        }
        return actions;
    }

    static void finishTracks(EditorScene scene, Prefab world, List<Integer> indices, String groupId) {
        for (int i = 0; i < indices.size(); i++) {
            KeyframeTrack track = scene.keyframeTracks.get(indices.get(i));
            Prefab.Track source = world.tracks().get(i);
            if (source.customName() != null) track.customName = source.customName();
            if (source.customColour() != 0) track.customColour = source.customColour();
            tag(track, groupId);
        }
    }
    public static void create(EditorScene scene, String name, Collection<Integer> tracks) {
        PrefabGroup group = new PrefabGroup(UUID.randomUUID().toString(), name, null, null, 1, 0);
        groups(scene).put(group.id(), group);
        for (int index : tracks) tag(scene.keyframeTracks.get(index), group.id());
    }

    public static void rename(EditorScene scene, PrefabGroup group, String name) {
        groups(scene).put(group.id(), group.withName(name));
    }

    public static List<SelectedKeyframes> selection(EditorScene scene, Span span) {
        List<SelectedKeyframes> selection = new ArrayList<>();
        for (int index : span.tracks()) {
            KeyframeTrack track = scene.keyframeTracks.get(index);
            selection.add(new SelectedKeyframes(track.keyframeType, index, new IntOpenHashSet(track.keyframesByTick.keySet())));
        }
        return selection;
    }

    public static void dissolve(EditorScene scene, Span span) {
        for (int index : span.tracks()) tag(scene.keyframeTracks.get(index), null);
        groups(scene).remove(span.group().id());
    }

    private static List<OldTrack> snapshot(EditorScene scene, Span span) {
        List<OldTrack> old = new ArrayList<>();
        for (int index : span.tracks()) {
            KeyframeTrack track = scene.keyframeTracks.get(index);
            TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
            track.keyframesByTick.forEach((tick, keyframe) -> keyframes.put(tick, keyframe.copy()));
            old.add(new OldTrack(index, track.keyframeType, track.customName, track.customColour, keyframes));
        }
        return old;
    }

    private static List<EditorSceneHistoryAction> restore(List<OldTrack> old) {
        List<EditorSceneHistoryAction> actions = new ArrayList<>();
        for (OldTrack track : old) {
            actions.add(new EditorSceneHistoryAction.AddTrack(track.type(), track.index()));
            track.keyframes().forEach((tick, keyframe) ->
                    actions.add(new EditorSceneHistoryAction.SetKeyframe(track.type(), track.index(), tick, keyframe)));
        }
        return actions;
    }
}
