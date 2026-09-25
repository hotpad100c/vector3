package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.CameraRotation;
import com.moulberry.flashback.visuals.ReplayVisuals;
import ml.mypals.vectorthree.camera.shake.CameraShake;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CameraRotation.class, remap = false)
public class CameraRotationShakeMixin {
    @Inject(method = "modifyViewQuaternion", at = @At("HEAD"), cancellable = true)
    private static void vector3$shake(Quaternionf view, CallbackInfoReturnable<Quaternionf> cir) {
        EditorState state = EditorStateManager.getCurrent();
        if (state == null || ReplayUI.isMovingCamera()) {
            cir.setReturnValue(view);
            return;
        }
        ReplayVisuals visuals = state.replayVisuals;
        Quaternionf result = new Quaternionf(view);
        if (visuals.overrideRoll) result.rotateZ((float) Math.toRadians(visuals.overrideRollAmount));
        CameraShake.Sample sample = CameraShake.current();
        if (sample != null) result.rotateYXZ(sample.yaw(), sample.pitch(), sample.roll());
        cir.setReturnValue(result);
    }
}
