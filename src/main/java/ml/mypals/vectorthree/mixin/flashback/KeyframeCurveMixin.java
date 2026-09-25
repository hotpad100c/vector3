package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import ml.mypals.vectorthree.flashback.curve.CurveHolder;
import ml.mypals.vectorthree.flashback.curve.SpeedCurve;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Keyframe.class, remap = false)
public class KeyframeCurveMixin implements CurveHolder {
    @Unique private transient SpeedCurve vector3$curve;

    @Override public SpeedCurve vector3$curve() { return vector3$curve; }
    @Override public void vector3$setCurve(SpeedCurve curve) { vector3$curve = curve; }
}
