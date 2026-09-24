package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.prefab.PrefabGroupStore;
import ml.mypals.vectorthree.prefab.PrefabGroupHolder;
import ml.mypals.vectorthree.prefab.PrefabHistory;
import ml.mypals.vectorthree.shape.ShapeTimelineSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(EditorScene.class)
public class EditorSceneMixin implements PrefabGroupHolder.Scene {
    @Shadow public List<KeyframeTrack> keyframeTracks;
    // Saved with the scene through PrefabGroupStore's adapter; Gson skips field initializers, so it's created lazily.
    @Unique private PrefabGroupStore vector3$prefabGroups;

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
