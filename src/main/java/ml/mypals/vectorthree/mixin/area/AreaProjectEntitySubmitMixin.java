package ml.mypals.vectorthree.mixin.area;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.vectorthree.shape.area.AreaProjection;
import ml.mypals.vectorthree.shape.area.AreaTintedCollector;
import ml.mypals.vectorthree.shape.area.ProjectedEntityState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public class AreaProjectEntitySubmitMixin {
    @WrapOperation(method = "submitEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void vector3$submitAtDestination(EntityRenderDispatcher dispatcher, EntityRenderState state,
            CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector,
            Operation<Void> original) {
        AreaProjection.Projection projection = ((ProjectedEntityState) state).vector3$projection();
        if (projection == null) {
            original.call(dispatcher, state, camera, x, y, z, poseStack, collector);
            return;
        }
        Vec3 dest = projection.map(state.x, state.y, state.z);
        poseStack.pushPose();
        poseStack.translate(dest.x - (state.x - x), dest.y - (state.y - y), dest.z - (state.z - z));
        poseStack.mulPose(projection.linear());
        original.call(dispatcher, state, camera, 0.0, 0.0, 0.0, poseStack,
                projection.translucent() ? new AreaTintedCollector(collector, projection.alpha()) : collector);
        poseStack.popPose();
    }
}
