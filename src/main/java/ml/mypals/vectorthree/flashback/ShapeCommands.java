package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import ml.mypals.vectorthree.prefab.PrefabGroups;
import ml.mypals.vectorthree.shape.ShapeState;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Whole-shape commands: every keyframe of a shape, on whatever Shape track it sits, is handled together. */
public final class ShapeCommands {
    private ShapeCommands() {}

    public static Set<String> shapesIn(EditorScene scene, List<SelectedKeyframes> selection) {
        Set<String> shapes = new LinkedHashSet<>();
        for (SelectedKeyframes selected : selection) {
            if (selected.trackIndex() < 0 || selected.trackIndex() >= scene.keyframeTracks.size()) continue;
            KeyframeTrack track = scene.keyframeTracks.get(selected.trackIndex());
            selected.keyframeTicks().forEach(tick -> {
                if (track.keyframesByTick.get(tick) instanceof ShapeKeyframe shape) shapes.add(shape.value.shapeId());
            });
        }
        return shapes;
    }

    /** Removes the shapes with all their keyframes; returns the history entry, or null when there was nothing. */
    public static EditorSceneHistoryEntry delete(EditorScene scene, Set<String> shapes) {
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        for (int index = 0; index < scene.keyframeTracks.size(); index++) {
            KeyframeTrack track = scene.keyframeTracks.get(index);
            if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                if (!(entry.getValue() instanceof ShapeKeyframe shape) || !shapes.contains(shape.value.shapeId())) continue;
                redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, index, entry.getKey()));
                undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, index, entry.getKey(), shape.copy()));
            }
        }
        if (redo.isEmpty()) return null;
        return new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.delete_shapes", shapes.size()));
    }

    public record Duplicate(EditorSceneHistoryEntry entry, List<SelectedKeyframes> selection) {}

    /**
     * Copies each shape, keyframes and all, onto a new track of its own under a fresh id. Parents that are copied
     * along are swapped for their copies, so a duplicated hierarchy stays self-contained.
     */
    public static Duplicate duplicate(EditorScene scene, Set<String> shapes) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (String shape : shapes) ids.put(shape, "vector3:timeline/" + UUID.randomUUID());
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        List<SelectedKeyframes> selection = new ArrayList<>();
        int index = scene.keyframeTracks.size();
        for (Map.Entry<String, String> copy : ids.entrySet()) {
            IntOpenHashSet ticks = new IntOpenHashSet();
            List<EditorSceneHistoryAction> keyframes = new ArrayList<>();
            for (KeyframeTrack track : scene.keyframeTracks) {
                if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
                for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                    if (!(entry.getValue() instanceof ShapeKeyframe shape) || !shape.value.shapeId().equals(copy.getKey())) continue;
                    ShapeKeyframe duplicate = (ShapeKeyframe) shape.copy();
                    duplicate.value = copied(shape.value, copy.getValue(), ids);
                    PrefabGroups.tag(duplicate, null);
                    keyframes.add(new EditorSceneHistoryAction.SetKeyframe(ShapeKeyframeType.INSTANCE, index, entry.getKey(), duplicate));
                    ticks.add(entry.getKey().intValue());
                }
            }
            if (keyframes.isEmpty()) continue;
            redo.add(new EditorSceneHistoryAction.AddTrack(ShapeKeyframeType.INSTANCE, index));
            redo.addAll(keyframes);
            undo.addFirst(new EditorSceneHistoryAction.RemoveTrack(ShapeKeyframeType.INSTANCE, index));
            selection.add(new SelectedKeyframes(ShapeKeyframeType.INSTANCE, index, ticks));
            index++;
        }
        if (redo.isEmpty()) return null;
        return new Duplicate(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.duplicate_shapes", selection.size())),
                selection);
    }

    private static ShapeState copied(ShapeState state, String id, Map<String, String> ids) {
        ShapeState copy = state.withIdentity(state.shapeType(), id);
        String parent = state.parentShapeId();
        if (parent != null && ids.containsKey(parent)) copy = copy.withParent(ids.get(parent));
        if (state.name() != null && !state.name().isBlank()) copy = copy.withName(I18n.get("vector3.shape.copy_name", state.name()));
        return copy;
    }
}
