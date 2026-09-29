package ml.mypals.vectorthree.mixin.minecraft;

import ml.mypals.vectorthree.core.port.Ports;
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
        var shake = Ports.view().cameraShake();
        if (shake != null) move(shake.forward(), shake.up(), shake.left());
    }
}
