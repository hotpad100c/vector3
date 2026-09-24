package ml.mypals.vectorthree.mixin.flashback;

import com.google.common.collect.Maps;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(value = CameraOrbitKeyframe.class, remap = false)
public abstract class CameraOrbitKeyframeMixin implements OrbitTilt {
    @Unique private float vector3$tiltX;
    @Unique private float vector3$tiltZ;

    @Override public float vector3$tiltX() { return vector3$tiltX; }
    @Override public float vector3$tiltZ() { return vector3$tiltZ; }

    @Override
    public void vector3$setTilt(float tiltX, float tiltZ) {
        vector3$tiltX = tiltX;
        vector3$tiltZ = tiltZ;
    }

    @Unique
    private static void vector3$tiltChange(KeyframeChange change, double tiltX, double tiltZ) {
        if (change instanceof OrbitTilt tilted) tilted.vector3$setTilt((float) tiltX, (float) tiltZ);
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private void vector3$copyTilt(CallbackInfoReturnable<Keyframe> cir) {
        ((OrbitTilt) cir.getReturnValue()).vector3$setTilt(vector3$tiltX, vector3$tiltZ);
    }

    @Inject(method = "createChange", at = @At("RETURN"))
    private void vector3$changeTilt(CallbackInfoReturnable<KeyframeChange> cir) {
        vector3$tiltChange(cir.getReturnValue(), vector3$tiltX, vector3$tiltZ);
    }

    @Inject(method = "createSmoothInterpolatedChange", at = @At("RETURN"))
    private void vector3$smoothTilt(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3,
            float amount, CallbackInfoReturnable<KeyframeChange> cir) {
        OrbitTilt a = (OrbitTilt) p1, b = (OrbitTilt) p2, c = (OrbitTilt) p3;
        vector3$tiltChange(cir.getReturnValue(),
                CatmullRom.value(vector3$tiltX, a.vector3$tiltX(), b.vector3$tiltX(), c.vector3$tiltX(),
                        t1 - t0, t2 - t0, t3 - t0, amount),
                CatmullRom.value(vector3$tiltZ, a.vector3$tiltZ(), b.vector3$tiltZ(), c.vector3$tiltZ(),
                        t1 - t0, t2 - t0, t3 - t0, amount));
    }

    @Inject(method = "createHermiteInterpolatedChange", at = @At("RETURN"))
    private void vector3$hermiteTilt(Map<Float, Keyframe> keyframes, float amount,
            CallbackInfoReturnable<KeyframeChange> cir) {
        vector3$tiltChange(cir.getReturnValue(),
                Hermite.value(Maps.transformValues(keyframes, k -> (double) ((OrbitTilt) k).vector3$tiltX()), amount),
                Hermite.value(Maps.transformValues(keyframes, k -> (double) ((OrbitTilt) k).vector3$tiltZ()), amount));
    }

    @Inject(method = "renderEditKeyframe", at = @At("TAIL"))
    private void vector3$editTilt(Consumer<Consumer<Keyframe>> update, CallbackInfo ci) {
        float[] tilt = {vector3$tiltX, vector3$tiltZ};
        if (ImGui.dragFloat2(I18n.get("vector3.orbit.tilt"), tilt, 0.5f)) {
            float tiltX = tilt[0], tiltZ = tilt[1];
            update.accept(keyframe -> ((OrbitTilt) keyframe).vector3$setTilt(tiltX, tiltZ));
        }
    }
}
