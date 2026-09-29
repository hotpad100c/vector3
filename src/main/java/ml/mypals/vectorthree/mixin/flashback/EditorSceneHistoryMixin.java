package ml.mypals.vectorthree.mixin.flashback;

import ml.mypals.vectorthree.fb.clips.ClipProject;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistory;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import ml.mypals.vectorthree.fb.prefab.PrefabHistory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// Undo steps position back then applies entries[position]; redo applies entries[position] then steps forward.
@Mixin(value = EditorSceneHistory.class, remap = false)
public class EditorSceneHistoryMixin implements ClipProject.ResettableHistory, ml.mypals.vectorthree.fb.editor.HistoryWindow.HistoryView, PrefabHistory.Store {
    @Shadow @Final private List<EditorSceneHistoryEntry> entries;
    @Shadow private int position;
    @Unique private int vector3$positionBefore;
    @Unique private int vector3$lastActiveId;
    @Unique private double vector3$lastPressTime;
    @Unique private String vector3$lastDescription;
    @Unique private final Map<EditorSceneHistoryEntry, PrefabHistory.Change> vector3$changes = new IdentityHashMap<>();

    /*
     * Keyframe edits pushed while one widget stays held down (dragging a value) become a single step: the first
     * push's undo and the latest redo. The same press is required, so two clicks on one checkbox stay two steps.
     * Only entries of plain keyframe sets merge, so nothing that adds or removes tracks (see PrefabHistory) is touched.
     */
    @Inject(method = "push", at = @At("RETURN"))
    private void vector3$mergeDrag(EditorScene scene, EditorSceneHistoryEntry entry, CallbackInfo ci) {
        boolean ui = com.moulberry.flashback.editor.ui.ReplayUI.isActive();
        int active = ui ? imgui.moulberry90.internal.ImGui.getActiveID() : 0;
        double pressTime = ui ? imgui.moulberry90.ImGui.getIO().getMouseClickedTime(0) : 0;
        boolean held = active != 0 && imgui.moulberry90.ImGui.isMouseDown(0);
        int size = entries.size();
        if (held && active == vector3$lastActiveId && pressTime == vector3$lastPressTime && size >= 2 && position == size
                && entry.description().equals(vector3$lastDescription)
                && vector3$onlySetsKeyframes(entries.get(size - 2)) && vector3$onlySetsKeyframes(entry)) {
            EditorSceneHistoryEntry first = entries.get(size - 2);
            entries.remove(size - 1);
            entries.set(size - 2, new EditorSceneHistoryEntry(first.undo(), entry.redo(), entry.description()));
            position--;
        }
        vector3$lastActiveId = held ? active : 0;
        vector3$lastPressTime = pressTime;
        vector3$lastDescription = entry.description();
        vector3$dropForgottenChanges();
    }

    // Pushing cuts off the redo tail, and merging drops an entry; their remembered track fields go with them.
    @Unique
    private void vector3$dropForgottenChanges() {
        if (vector3$changes.size() <= entries.size()) return;
        Set<EditorSceneHistoryEntry> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(entries);
        vector3$changes.keySet().removeIf(entry -> !live.contains(entry));
    }

    @Override public Map<EditorSceneHistoryEntry, PrefabHistory.Change> vector3$prefabChanges() { return vector3$changes; }

    @Unique
    private static boolean vector3$onlySetsKeyframes(EditorSceneHistoryEntry entry) {
        for (EditorSceneHistoryAction action : entry.undo())
            if (!(action instanceof EditorSceneHistoryAction.SetKeyframe)) return false;
        for (EditorSceneHistoryAction action : entry.redo())
            if (!(action instanceof EditorSceneHistoryAction.SetKeyframe)) return false;
        return true;
    }

    @Override
    public void vector3$reset() {
        entries.clear();
        vector3$changes.clear();
        position = 0;
    }

    @Override public List<EditorSceneHistoryEntry> vector3$entries() { return List.copyOf(entries); }
    @Override public int vector3$position() { return position; }

    @Inject(method = {"undo", "redo"}, at = @At("HEAD"))
    private void vector3$rememberPosition(EditorScene scene, Consumer<String> description, CallbackInfo ci) {
        vector3$positionBefore = position;
        vector3$lastActiveId = 0;
    }

    @Inject(method = "undo", at = @At("RETURN"))
    private void vector3$restoreUndoneTracks(EditorScene scene, Consumer<String> description, CallbackInfo ci) {
        if (position < vector3$positionBefore) PrefabHistory.undone(scene, entries.get(position));
    }

    @Inject(method = "redo", at = @At("RETURN"))
    private void vector3$restoreRedoneTracks(EditorScene scene, Consumer<String> description, CallbackInfo ci) {
        if (position > vector3$positionBefore) PrefabHistory.redone(scene, entries.get(position - 1));
    }
}
