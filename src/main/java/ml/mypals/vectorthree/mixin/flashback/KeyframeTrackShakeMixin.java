package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.types.CameraShakeKeyframeType;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import ml.mypals.vectorthree.camera.shake.CameraShake;
import ml.mypals.vectorthree.camera.shake.ShakeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.TreeMap;

@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackShakeMixin {
    @Shadow @Final public KeyframeType<?> keyframeType;
    @Shadow public TreeMap<Integer, Keyframe> keyframesByTick;

    @Inject(method = "createKeyframeChange", at = @At("RETURN"), cancellable = true)
    private void vector3$shakePhase(float tick, RealTimeMapping mapping, CallbackInfoReturnable<KeyframeChange> cir) {
        if (keyframeType != CameraShakeKeyframeType.INSTANCE) return;
        KeyframeChange change = cir.getReturnValue();
        if (change == null && !keyframesByTick.isEmpty() && tick >= keyframesByTick.lastKey()) {
            change = keyframesByTick.lastEntry().getValue().createChange();
            cir.setReturnValue(change);
        }
        if (change instanceof ShakeHolder holder) holder.vector3$setPhases(CameraShake.phases(keyframesByTick, tick, mapping));
    }
}
