package ml.mypals.vectorthree.mixin.minecraft.area;

import ml.mypals.vectorthree.mc.shape.area.AreaSuppression;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLightEngine.class)
public class AreaLightFlushMixin {
    @Inject(method = "runLightUpdates", at = @At("RETURN"))
    private void vector3$relightAfterQueuedData(CallbackInfoReturnable<Integer> cir) {
        AreaSuppression.afterLightUpdates((LevelLightEngine) (Object) this);
    }
}
