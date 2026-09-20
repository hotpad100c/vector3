package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EditorScene.class)
public class EditorSceneMixin {
    @Shadow public List<KeyframeTrack> keyframeTracks;

    @Inject(method = "setKeyframe", at = @At("HEAD"), remap = false)
    private void vector3$keepShapeIdentity(int trackIndex, int tick, Keyframe keyframe, CallbackInfo ci) {
        if (!(keyframe instanceof ShapeKeyframe added)
                || trackIndex < 0 || trackIndex >= keyframeTracks.size()) return;

        KeyframeTrack track = keyframeTracks.get(trackIndex);
        if (track.keyframesByTick.get(tick) instanceof ShapeKeyframe edited) {
            if (!edited.state.shapeId().equals(added.state.shapeId())
                    || !edited.state.shapeType().equals(added.state.shapeType())) {
                for (Keyframe existing : track.keyframesByTick.values()) {
                    if (existing instanceof ShapeKeyframe shape) {
                        shape.state = shape.state.withIdentity(added.state.shapeType(), added.state.shapeId());
                    }
                }
            }
            return;
        }

        for (Keyframe existing : track.keyframesByTick.values()) {
            if (existing instanceof ShapeKeyframe shape && existing != added) {
                added.state = added.state.withIdentity(shape.state.shapeType(), shape.state.shapeId());
                return;
            }
        }
    }
}
