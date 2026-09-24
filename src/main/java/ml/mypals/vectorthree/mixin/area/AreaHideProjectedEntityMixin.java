package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.area.AreaProjection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class AreaHideProjectedEntityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void vector3$hideProjected(E entity, Frustum frustum, double camX, double camY,
            double camZ, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (AreaProjection.forEntity(entity) != null) cir.setReturnValue(false);
    }
}
