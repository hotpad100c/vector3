package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.resources.language.I18n;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class PrefabGroups {
    public record Span(PrefabGroup group, List<Integer> tracks, int firstTick, int lastTick,
                       Map<Integer, IntSortedSet> members) {}

    /** A clickable part of a group on the timeline; {@code row} is -1 for the bar, else a track row. */
    public record Handle(String groupId, float left, float top, float right, float bottom, int row) {}

    public static final class Drag {
        private final String groupId;
        private final Map<Integer, TreeMap<Integer, Keyframe>> originals = new LinkedHashMap<>();
        private final Map<Integer, IntSortedSet> members;
        private final int firstTick;
        private int applied;

        public Drag(EditorScene scene, Span span) {
            groupId = span.group().id();
            firstTick = span.firstTick();
            members = span.members();
            for (int index : span.tracks()) originals.put(index, scene.keyframeTracks.get(index).keyframesByTick);
        }

        public boolean update(EditorScene scene, int delta) {
            delta = Math.max(delta, -firstTick);
            if (delta == applied || !valid(scene)) return false;
            for (Map.Entry<Integer, TreeMap<Integer, Keyframe>> entry : originals.entrySet()) {
                IntSortedSet ticks = members.get(entry.getKey());
                TreeMap<Integer, Keyframe> shifted = new TreeMap<>(entry.getValue());
                for (int tick : ticks) shifted.remove(tick);
                for (int tick : ticks) shifted.put(tick + delta, entry.getValue().get(tick));
                scene.keyframeTracks.get(entry.getKey()).keyframesByTick = shifted;
            }
            applied = delta;
            return true;
        }

        public @Nullable EditorSceneHistoryEntry finish(EditorScene scene) {
            if (!valid(scene)) return null;
            restore(scene);
            if (applied == 0) return null;
            for (Span span : spans(scene)) {
                if (span.group().id().equals(groupId)) return move(scene, span, applied);
            }
            return null;
        }

        public void cancel(EditorScene scene) {
            if (valid(scene)) restore(scene);
            applied = 0;
        }

        private void restore(EditorScene scene) {
            originals.forEach((index, keyframes) -> scene.keyframeTracks.get(index).keyframesByTick = keyframes);
        }

        private boolean valid(EditorScene scene) {
            for (int index : originals.keySet()) {
                if (index >= scene.keyframeTracks.size()) return false;
            }
            return true;
        }
    }

    private record OldTrack(int index, KeyframeType<?> type, TreeMap<Integer, Keyframe> keyframes) {}

    private PrefabGroups() {}

    public static Map<String, PrefabGroup> groups(EditorScene scene) {
        return ((PrefabGroupHolder.Scene) scene).vector3$prefabGroups().groups;
    }

    public static @Nullable String groupOf(Keyframe keyframe) {
        return ((PrefabGroupHolder.Keyframe) keyframe).vector3$group();
    }

    public static void tag(Keyframe keyframe, @Nullable String id) {
        ((PrefabGroupHolder.Keyframe) keyframe).vector3$setGroup(id);
    }

    public static void writeGroup(Keyframe keyframe, JsonObject json) {
        String group = groupOf(keyframe);
        if (group != null) json.addProperty("vector3_group", group);
    }

    public static void readGroup(Keyframe keyframe, JsonObject json) {
        if (json.has("vector3_group")) tag(keyframe, json.get("vector3_group").getAsString());
    }

    // Saves from before per-keyframe groups tagged whole tracks.
    private static void migrateTrackTags(EditorScene scene) {
        for (KeyframeTrack track : scene.keyframeTracks) {
            PrefabGroupHolder.Track holder = (PrefabGroupHolder.Track) track;
            String id = holder.vector3$prefabGroup();
            if (id == null) continue;
            for (Keyframe keyframe : track.keyframesByTick.values()) {
                if (groupOf(keyframe) == null) tag(keyframe, id);
            }
            holder.vector3$setPrefabGroup(null);
        }
    }

    public static List<Span> spans(EditorScene scene) {
        migrateTrackTags(scene);
        Map<String, Map<Integer, IntSortedSet>> members = new LinkedHashMap<>();
        for (int i = 0; i < scene.keyframeTracks.size(); i++) {
            for (Map.Entry<Integer, Keyframe> entry : scene.keyframeTracks.get(i).keyframesByTick.entrySet()) {
                String id = groupOf(entry.getValue());
                if (id == null || !groups(scene).containsKey(id)) continue;
                members.computeIfAbsent(id, key -> new TreeMap<>())
                        .computeIfAbsent(i, key -> new IntAVLTreeSet()).add((int) entry.getKey());
            }
        }
        List<Span> spans = new ArrayList<>();
        members.forEach((id, rows) -> {
            int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
            for (IntSortedSet ticks : rows.values()) {
                first = Math.min(first, ticks.firstInt());
                last = Math.max(last, ticks.lastInt());
            }
            spans.add(new Span(groups(scene).get(id), new ArrayList<>(rows.keySet()), first, last, rows));
        });
        return spans;
    }

    public static EditorSceneHistoryEntry move(EditorScene scene, Span span, int delta) {
        delta = Math.max(delta, -span.firstTick());
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        List<EditorSceneHistoryAction> undoSets = new ArrayList<>(), redoSets = new ArrayList<>();
        for (Map.Entry<Integer, IntSortedSet> row : span.members().entrySet()) {
            int index = row.getKey();
            KeyframeTrack track = scene.keyframeTracks.get(index);
            for (int from : row.getValue()) {
                Keyframe keyframe = track.keyframesByTick.get(from);
                int to = from + delta;
                redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, index, from));
                redoSets.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, to, keyframe.copy()));
                undo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, index, to));
                undoSets.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, from, keyframe.copy()));
                Keyframe covered = track.keyframesByTick.get(to);
                if (covered != null && !row.getValue().contains(to)) {
                    undoSets.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, to, covered.copy()));
                }
            }
        }
        // Every remove before any set, so keyframes moving onto each other's old ticks survive.
        redo.addAll(redoSets);
        undo.addAll(undoSets);
        groups(scene).put(span.group().id(), span.group().withStartTick(span.group().startTick() + delta));
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.prefab.history.move", span.group().name()));
    }

    public static EditorSceneHistoryEntry delete(EditorScene scene, Span span) {
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        removeMembers(scene, span, undo, redo);
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.prefab.history.delete", span.group().name()));
    }

    public static EditorSceneHistoryEntry replace(EditorScene scene, Span span, Prefab world, int startTick,
            float timeScale, List<Integer> newIndices) {
        List<EditorSceneHistoryAction> undoRemoval = new ArrayList<>(), redo = new ArrayList<>();
        removeMembers(scene, span, undoRemoval, redo);
        int base = span.tracks().getFirst();
        redo.addAll(add(world, base, startTick, timeScale));
        List<EditorSceneHistoryAction> undo = new ArrayList<>();
        for (int i = world.tracks().size() - 1; i >= 0; i--) {
            undo.add(new EditorSceneHistoryAction.RemoveTrack(world.tracks().get(i).type(), base + i));
        }
        undo.addAll(undoRemoval);
        for (int i = 0; i < world.tracks().size(); i++) newIndices.add(base + i);
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.prefab.history.edit", span.group().name()));
    }

    // Tracks holding nothing but the group's keyframes go with it; other tracks only lose those keyframes.
    private static void removeMembers(EditorScene scene, Span span, List<EditorSceneHistoryAction> undo,
            List<EditorSceneHistoryAction> redo) {
        List<OldTrack> whole = new ArrayList<>();
        List<EditorSceneHistoryAction> restoreKeyframes = new ArrayList<>();
        for (Map.Entry<Integer, IntSortedSet> row : span.members().entrySet()) {
            int index = row.getKey();
            KeyframeTrack track = scene.keyframeTracks.get(index);
            if (row.getValue().size() == track.keyframesByTick.size()) {
                TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
                track.keyframesByTick.forEach((tick, keyframe) -> keyframes.put(tick, keyframe.copy()));
                whole.add(new OldTrack(index, track.keyframeType, keyframes));
                continue;
            }
            for (int tick : row.getValue()) {
                redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, index, tick));
                restoreKeyframes.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, tick,
                        track.keyframesByTick.get(tick).copy()));
            }
        }
        for (int i = whole.size() - 1; i >= 0; i--) {
            redo.add(new EditorSceneHistoryAction.RemoveTrack(whole.get(i).type(), whole.get(i).index()));
        }
        for (OldTrack track : whole) {
            undo.add(new EditorSceneHistoryAction.AddTrack(track.type(), track.index()));
            track.keyframes().forEach((tick, keyframe) ->
                    undo.add(new EditorSceneHistoryAction.SetKeyframe(track.type(), track.index(), tick, keyframe)));
        }
        undo.addAll(restoreKeyframes);
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
            for (Keyframe keyframe : track.keyframesByTick.values()) tag(keyframe, groupId);
        }
    }

    public static void create(EditorScene scene, String name, List<SelectedKeyframes> selection) {
        PrefabGroup group = new PrefabGroup(UUID.randomUUID().toString(), name, null, null, 1, 0);
        groups(scene).put(group.id(), group);
        tagSelection(scene, selection, group.id());
    }

    public static void tagSelection(EditorScene scene, List<SelectedKeyframes> selection, @Nullable String id) {
        for (SelectedKeyframes selected : selection) {
            if (selected.trackIndex() >= scene.keyframeTracks.size()) continue;
            TreeMap<Integer, Keyframe> keyframes = scene.keyframeTracks.get(selected.trackIndex()).keyframesByTick;
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = keyframes.get(tick);
                if (keyframe != null) tag(keyframe, id);
            }
        }
    }

    public static List<SelectedKeyframes> wholeTrack(EditorScene scene, int trackIndex) {
        KeyframeTrack track = scene.keyframeTracks.get(trackIndex);
        return List.of(new SelectedKeyframes(track.keyframeType, trackIndex, new IntOpenHashSet(track.keyframesByTick.keySet())));
    }

    public static void rename(EditorScene scene, PrefabGroup group, String name) {
        groups(scene).put(group.id(), group.withName(name));
    }

    public static List<SelectedKeyframes> selection(EditorScene scene, Span span) {
        List<SelectedKeyframes> selection = new ArrayList<>();
        span.members().forEach((index, ticks) -> selection.add(new SelectedKeyframes(
                scene.keyframeTracks.get(index).keyframeType, index, new IntOpenHashSet(ticks))));
        return selection;
    }

    public static void dissolve(EditorScene scene, Span span) {
        tagSelection(scene, selection(scene, span), null);
        groups(scene).remove(span.group().id());
    }
}
