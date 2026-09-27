package ml.mypals.vectorthree.mixin.particle;

import ml.mypals.vectorthree.shape.particle.ParticleEmitters;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineEmitterMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void vector3$emitterModules(CallbackInfo ci) {
        ParticleEmitters.tick();
    }
}
