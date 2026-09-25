package ml.mypals.vectorthree.mixin.flashback;

import ml.mypals.vectorthree.flashback.curve.SpeedCurves;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.AudioKeyframe;
import com.moulberry.flashback.keyframe.impl.BlockOverrideKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.FreezeKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe;
import ml.mypals.vectorthree.prefab.PrefabGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {AudioKeyframe.class, BlockOverrideKeyframe.class, CameraKeyframe.class, CameraOrbitKeyframe.class,
        CameraShakeKeyframe.class, FOVKeyframe.class, FreezeKeyframe.class, TickrateKeyframe.class,
        TimeOfDayKeyframe.class, TimelapseKeyframe.class, TrackEntityKeyframe.class}, remap = false)
public class KeyframeCopyGroupMixin {
    @Inject(method = "copy", at = @At("RETURN"))
    private void vector3$copyGroup(CallbackInfoReturnable<Keyframe> cir) {
        PrefabGroups.tag(cir.getReturnValue(), PrefabGroups.groupOf((Keyframe) (Object) this));
        SpeedCurves.set(cir.getReturnValue(), SpeedCurves.of((Keyframe) (Object) this));
    }
}
