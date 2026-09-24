package ml.mypals.vectorthree.mixin.area;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ml.mypals.vectorthree.shape.area.AreaProjection;
import net.minecraft.client.particle.SingleQuadParticle;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Opaque layers ignore alpha, so projected particles of a translucent area move to the translucent ones.
@Mixin(SingleQuadParticle.class)
public class AreaParticleAlphaMixin {
    @ModifyExpressionValue(method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/SingleQuadParticle;getLayer()Lnet/minecraft/client/particle/SingleQuadParticle$Layer;"))
    private SingleQuadParticle.Layer vector3$translucentLayer(SingleQuadParticle.Layer layer) {
        if (AreaProjection.particleAlpha() >= 1.0f || layer.translucent()) return layer;
        if (layer == SingleQuadParticle.Layer.OPAQUE) return SingleQuadParticle.Layer.TRANSLUCENT;
        if (layer == SingleQuadParticle.Layer.OPAQUE_TERRAIN) return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
        if (layer == SingleQuadParticle.Layer.OPAQUE_ITEMS) return SingleQuadParticle.Layer.TRANSLUCENT_ITEMS;
        return layer;
    }

    @ModifyExpressionValue(method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/particle/SingleQuadParticle;alpha:F", opcode = Opcodes.GETFIELD))
    private float vector3$areaAlpha(float alpha) {
        return alpha * AreaProjection.particleAlpha();
    }
}
