package ml.mypals.vectorthree.multiedit;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The selected keyframes, each with a working copy that edits land on until they are committed together. */
public final class MultiSelection {
    public static final class Entry {
        public final KeyframeType<?> type;
        public final int track, tick;
        public final Keyframe original, working;
        boolean touched;

        Entry(KeyframeType<?> type, int track, int tick, Keyframe original) {
            this.type = type;
            this.track = track;
            this.tick = tick;
            this.original = original;
            this.working = original.copy();
        }

        public Consumer<Consumer<Keyframe>> update() {
            return edit -> {
                edit.accept(working);
                touched = true;
            };
        }

        public void touch() {
            touched = true;
        }
    }

    private MultiSelection() {}

    public static int count(List<SelectedKeyframes> selection) {
        int count = 0;
        for (SelectedKeyframes selected : selection) count += selected.keyframeTicks().size();
        return count;
    }

    /** Every selected keyframe; the one being edited, when there is one, comes first. */
    public static List<Entry> gather(EditorScene scene, List<SelectedKeyframes> selection, int editingTrack, int editingTick) {
        List<Entry> entries = new ArrayList<>();
        for (SelectedKeyframes selected : selection) {
            if (selected.trackIndex() < 0 || selected.trackIndex() >= scene.keyframeTracks.size()) continue;
            KeyframeTrack track = scene.keyframeTracks.get(selected.trackIndex());
            for (IntIterator ticks = selected.keyframeTicks().iterator(); ticks.hasNext(); ) {
                int tick = ticks.nextInt();
                Keyframe keyframe = track.keyframesByTick.get(tick);
                if (keyframe == null) continue;
                Entry entry = new Entry(track.keyframeType, selected.trackIndex(), tick, keyframe);
                if (selected.trackIndex() == editingTrack && tick == editingTick) entries.addFirst(entry);
                else entries.add(entry);
            }
        }
        return entries;
    }

    /** Pushes one history entry for every touched copy; returns whether anything changed. */
    public static boolean commit(List<Entry> entries, Runnable upgradeToWrite, java.util.function.Supplier<EditorScene> scene) {
        List<Entry> touched = entries.stream().filter(entry -> entry.touched).toList();
        if (touched.isEmpty()) return false;
        upgradeToWrite.run();
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        for (Entry entry : touched) {
            undo.add(new EditorSceneHistoryAction.SetKeyframe(entry.type, entry.track, entry.tick, entry.original.copy()));
            redo.add(new EditorSceneHistoryAction.SetKeyframe(entry.type, entry.track, entry.tick, entry.working.copy()));
        }
        scene.get().push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.multi_edit", touched.size())));
        return true;
    }
}
