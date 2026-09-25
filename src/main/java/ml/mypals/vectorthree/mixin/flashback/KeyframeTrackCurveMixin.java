package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.flashback.curve.SpeedCurve;
import ml.mypals.vectorthree.flashback.curve.SpeedCurves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackCurveMixin {
    @ModifyVariable(method = "createKeyframeChange", at = @At("STORE"), name = "leftInterpolation")
    private SidedInterpolationType vector3$linearLeft(SidedInterpolationType side,
            @Local(name = "lowerKeyframe") Keyframe lower) {
        return SpeedCurves.of(lower) != null ? SidedInterpolationType.LINEAR : side;
    }

    @ModifyVariable(method = "createKeyframeChange", at = @At("STORE"), name = "rightInterpolation")
    private SidedInterpolationType vector3$linearRight(SidedInterpolationType side,
            @Local(name = "lowerKeyframe") Keyframe lower) {
        return SpeedCurves.of(lower) != null ? SidedInterpolationType.LINEAR : side;
    }

    @ModifyVariable(method = "createKeyframeChange", at = @At("STORE"), name = "amount")
    private float vector3$curveAmount(float amount, @Local(name = "lowerKeyframe") Keyframe lower) {
        SpeedCurve curve = SpeedCurves.of(lower);
        return curve == null ? amount : curve.evaluate(amount);
    }
}
