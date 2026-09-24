package ml.mypals.vectorthree.mixin.area;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.shape.area.AreaProjection;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(QuadParticleGroup.class)
public class AreaProjectParticleMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"))
    private boolean vector3$keepProjectedParticles(Frustum frustum, double x, double y, double z, Operation<Boolean> original) {
        return original.call(frustum, x, y, z) || AreaProjection.forParticle(x, y, z) != null;
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"))
    private void vector3$projectParticle(SingleQuadParticle particle, QuadParticleRenderState state, Camera camera,
                                         float partialTickTime, Operation<Void> original) {
        ParticleAccessor position = (ParticleAccessor) particle;
        double x = position.vector3$x(), y = position.vector3$y(), z = position.vector3$z();
        AreaProjection.Projection projection = AreaProjection.forParticle(x, y, z);
        if (projection == null) {
            original.call(particle, state, camera, partialTickTime);
            return;
        }
        double xo = position.vector3$xo(), yo = position.vector3$yo(), zo = position.vector3$zo();
        Vec3 now = projection.map(x, y, z);
        Vec3 before = projection.map(xo, yo, zo);
        try {
            position.vector3$setX(now.x); position.vector3$setY(now.y); position.vector3$setZ(now.z);
            position.vector3$setXo(before.x); position.vector3$setYo(before.y); position.vector3$setZo(before.z);
            original.call(particle, state, camera, partialTickTime);
        } finally {
            position.vector3$setX(x); position.vector3$setY(y); position.vector3$setZ(z);
            position.vector3$setXo(xo); position.vector3$setYo(yo); position.vector3$setZo(zo);
        }
    }
}
