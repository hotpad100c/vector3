package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.state.EditorSceneHistory;
import ml.mypals.vectorthree.fb.clips.ClipProject;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.core.expression.GlobalVariable;
import ml.mypals.vectorthree.fb.expression.Globals;
import ml.mypals.vectorthree.fb.editor.HistoryWindow;
import ml.mypals.vectorthree.fb.timeline.PlaybackRange;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframe;
import ml.mypals.vectorthree.fb.prefab.PrefabGroupStore;
import ml.mypals.vectorthree.fb.prefab.PrefabGroupHolder;
import ml.mypals.vectorthree.fb.prefab.PrefabHistory;
import ml.mypals.vectorthree.core.shape.ShapeTimelineSelection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(EditorScene.class)
public class EditorSceneMixin implements PrefabGroupHolder.Scene, ClipProject.ClearableHistory,
        HistoryWindow.SceneHistory, PlaybackRange.Holder,
        Globals.Holder {
    @Final
    @Shadow public List<KeyframeTrack> keyframeTracks;
    @Unique private PrefabGroupStore vector3$prefabGroups;
    @Unique private boolean vector3$loopPlayback;
    // Saved with the scene by Flashback's reflective Gson.
    @Unique private GlobalVariable[] vector3$globals;

    @Override public GlobalVariable[] vector3$globals() { return vector3$globals; }
    @Override public void vector3$setGlobals(GlobalVariable[] globals) { vector3$globals = globals; }
    @Shadow @Final private EditorSceneHistory history;

    @Override
    public void vector3$clearHistory() {
        ((ClipProject.ResettableHistory) history).vector3$reset();
    }

    @Override public EditorSceneHistory vector3$history() { return history; }
    @Override public boolean vector3$loopPlayback() { return vector3$loopPlayback; }
    @Override public void vector3$setLoopPlayback(boolean enabled) { vector3$loopPlayback = enabled; }

    @Override
    public PrefabGroupStore vector3$prefabGroups() {
        if (vector3$prefabGroups == null) vector3$prefabGroups = new PrefabGroupStore();
        return vector3$prefabGroups;
    }

    @Inject(method = "setKeyframe", at = @At("HEAD"), remap = false)
    private void vector3$keepShapeIdentity(int trackIndex, int tick, Keyframe keyframe, CallbackInfo ci) {
        if (!(keyframe instanceof ShapeKeyframe added)
                || trackIndex < 0 || trackIndex >= keyframeTracks.size()) return;

        KeyframeTrack track = keyframeTracks.get(trackIndex);
        if (track.keyframesByTick.get(tick) instanceof ShapeKeyframe edited) {
            if (!edited.value.shapeId().equals(added.value.shapeId())
                    || !edited.value.shapeType().equals(added.value.shapeType())) {
                for (Keyframe existing : track.keyframesByTick.values()) {
                    if (existing instanceof ShapeKeyframe shape) {
                        shape.value = shape.value.withIdentity(added.value.shapeType(), added.value.shapeId());
                    }
                }
            }
            return;
        }

        for (Keyframe existing : track.keyframesByTick.values()) {
            if (existing instanceof ShapeKeyframe shape && existing != added) {
                added.value = added.value.withIdentity(shape.value.shapeType(), shape.value.shapeId());
                return;
            }
        }
    }

    @Inject(method = "push", at = @At("HEAD"), remap = false)
    private void vector3$beforePush(EditorSceneHistoryEntry entry, CallbackInfo ci) {
        PrefabHistory.beforePush((EditorScene) (Object) this);
    }

    @Inject(method = "push", at = @At("RETURN"), remap = false)
    private void vector3$afterPush(EditorSceneHistoryEntry entry, CallbackInfo ci) {
        PrefabHistory.afterPush((EditorScene) (Object) this, entry);
    }

    @Inject(method = {"undo", "redo"}, at = @At("RETURN"), remap = false)
    private void vector3$refreshShapesAfterHistory(Consumer<String> message, CallbackInfo ci) {
        ShapeTimelineSelection.requestRefresh();
    }
}
