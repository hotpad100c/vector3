package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import ml.mypals.vectorthree.fb.light.LightKeyframeType;
import ml.mypals.vectorthree.fb.fade.ScreenVFXKeyframeType;
import ml.mypals.vectorthree.fb.timeline.TrackManagement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.TreeMap;

@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackHoldMixin {
    @Shadow @Final public KeyframeType<?> keyframeType;
    @Shadow public TreeMap<Integer, Keyframe> keyframesByTick;
    @Shadow public boolean enabled;

    @Inject(method = "createKeyframeChange", at = @At("RETURN"), cancellable = true)
    private void vector3$holdLastValue(float tick, RealTimeMapping mapping, CallbackInfoReturnable<KeyframeChange> cir) {
        if ((keyframeType != LightKeyframeType.INSTANCE && keyframeType != ScreenVFXKeyframeType.INSTANCE)
                || cir.getReturnValue() != null || !enabled
                || !TrackManagement.audible((KeyframeTrack) (Object) this) || keyframesByTick.isEmpty()
                || tick < keyframesByTick.lastKey()) return;
        cir.setReturnValue(keyframesByTick.lastEntry().getValue().createChange());
    }
}
