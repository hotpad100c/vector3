package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPositionOrbit;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import ml.mypals.vectorthree.camera.orbit.OrbitMath;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyframeChangeCameraPositionOrbit.class, remap = false)
public abstract class CameraOrbitChangeMixin implements OrbitTilt {
    @Shadow @Final private Vector3d center;
    @Shadow @Final private double distance;
    @Shadow @Final private double yaw;
    @Shadow @Final private double pitch;

    @Unique private float vector3$tiltX;
    @Unique private float vector3$tiltZ;

    @Override public float vector3$tiltX() { return vector3$tiltX; }
    @Override public float vector3$tiltZ() { return vector3$tiltZ; }

    @Override
    public void vector3$setTilt(float tiltX, float tiltZ) {
        vector3$tiltX = tiltX;
        vector3$tiltZ = tiltZ;
    }

    @Inject(method = "interpolate", at = @At("RETURN"))
    private void vector3$interpolateTilt(KeyframeChange other, double amount, CallbackInfoReturnable<KeyframeChange> cir) {
        if (!(other instanceof OrbitTilt target) || !(cir.getReturnValue() instanceof OrbitTilt result)) return;
        result.vector3$setTilt((float) (vector3$tiltX + (target.vector3$tiltX() - vector3$tiltX) * amount),
                (float) (vector3$tiltZ + (target.vector3$tiltZ() - vector3$tiltZ) * amount));
    }

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void vector3$applyTilted(KeyframeHandler handler, CallbackInfo ci) {
        if (vector3$tiltX == 0 && vector3$tiltZ == 0) return;
        Vector3d offset = OrbitMath.eyeOffset(yaw, pitch, distance, vector3$tiltX, vector3$tiltZ);
        Vector3d position = new Vector3d(center).add(offset);
        double[] look = OrbitMath.lookAngles(offset, yaw);
        if (Minecraft.getInstance().player != null) position.y -= Minecraft.getInstance().player.getEyeHeight();
        handler.applyCameraPosition(position, look[0], look[1], 0);
        ci.cancel();
    }
}
