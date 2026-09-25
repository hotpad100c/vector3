package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.visuals.ReplayVisuals;
import ml.mypals.vectorthree.camera.shake.ShakeHolder;
import ml.mypals.vectorthree.camera.shake.ShakeParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ReplayVisuals.class, remap = false)
public class ReplayVisualsShakeMixin implements ShakeHolder {
    @Unique private transient ShakeParams vector3$shake;
    @Unique private transient double[] vector3$phases;

    @Override public ShakeParams vector3$shake() { return vector3$shake; }
    @Override public void vector3$setShake(ShakeParams params) { vector3$shake = params; }
    @Override public double[] vector3$phases() { return vector3$phases; }
    @Override public void vector3$setPhases(double[] phases) { vector3$phases = phases; }
}
