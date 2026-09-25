package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraShake;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import ml.mypals.vectorthree.camera.shake.ShakeHolder;
import ml.mypals.vectorthree.camera.shake.ShakeParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyframeChangeCameraShake.class, remap = false)
public abstract class CameraShakeChangeMixin implements ShakeHolder {
    @Unique private ShakeParams vector3$shake;
    @Unique private double[] vector3$phases;

    @Override public ShakeParams vector3$shake() { return vector3$shake; }
    @Override public void vector3$setShake(ShakeParams params) { vector3$shake = params; }
    @Override public double[] vector3$phases() { return vector3$phases; }
    @Override public void vector3$setPhases(double[] phases) { vector3$phases = phases; }

    @Inject(method = "interpolate", at = @At("RETURN"))
    private void vector3$interpolateShake(KeyframeChange other, double amount, CallbackInfoReturnable<KeyframeChange> cir) {
        if (!(other instanceof ShakeHolder target) || !(cir.getReturnValue() instanceof ShakeHolder result)) return;
        ShakeParams from = ShakeParams.orDefault(vector3$shake), to = ShakeParams.orDefault(target.vector3$shake());
        float[] a = from.floats(), b = to.floats();
        for (int i = 0; i < a.length; i++) a[i] += (float) ((b[i] - a[i]) * amount);
        result.vector3$setShake(ShakeParams.of(a, from.octaves(), from.seed()));
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private void vector3$applyShake(KeyframeHandler handler, CallbackInfo ci) {
        EditorState state = EditorStateManager.getCurrent();
        if (!(handler instanceof MinecraftKeyframeHandler) || state == null) return;
        ShakeHolder visuals = (ShakeHolder) state.replayVisuals;
        visuals.vector3$setShake(vector3$shake);
        visuals.vector3$setPhases(vector3$phases);
    }
}
