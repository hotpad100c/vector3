package ml.mypals.vectorthree.mixin;

import com.moulberry.flashback.Flashback;
import ml.mypals.vectorthree.camera.shake.CameraShake;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 1100)
public abstract class CameraShakePositionMixin {
    @Shadow protected abstract void move(float forwards, float up, float left);

    @Inject(method = "update", at = @At("RETURN"))
    private void vector3$shakePosition(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Flashback.isInReplay()) return;
        CameraShake.Sample sample = CameraShake.current();
        if (sample == null || (sample.right() == 0 && sample.up() == 0 && sample.forward() == 0)) return;
        move(sample.forward(), sample.up(), -sample.right());
    }
}
