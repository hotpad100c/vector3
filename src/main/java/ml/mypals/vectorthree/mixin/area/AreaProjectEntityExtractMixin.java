package ml.mypals.vectorthree.mixin.area;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.shape.area.AreaProjection;
import ml.mypals.vectorthree.shape.area.ProjectedEntityState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(LevelExtractor.class)
public class AreaProjectEntityExtractMixin {
    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;isEntityVisible(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDFJ)Z"))
    private boolean vector3$keepProjected(LevelExtractor extractor, Entity entity, Frustum frustum, double camX,
            double camY, double camZ, float partialTicks, long fadeDuration, Operation<Boolean> original) {
        return AreaProjection.forEntity(entity) != null
                || original.call(extractor, entity, frustum, camX, camY, camZ, partialTicks, fadeDuration);
    }

    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState vector3$tagProjected(LevelExtractor extractor, Entity entity, float partialTicks,
            Operation<EntityRenderState> original) {
        EntityRenderState state = original.call(extractor, entity, partialTicks);
        ((ProjectedEntityState) state).vector3$setProjection(AreaProjection.forEntity(entity));
        return state;
    }
}
