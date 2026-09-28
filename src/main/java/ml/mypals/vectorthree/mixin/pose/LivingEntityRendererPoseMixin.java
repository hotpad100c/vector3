package ml.mypals.vectorthree.mixin.pose;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.flashback.pose.EntityPoses;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererPoseMixin {
    // The layers (held items, ...) read the entity model's parts right after this, so they follow the pose.
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"))
    private void vector3$poseModel(EntityModel<?> model, Object state, Operation<Void> original) {
        original.call(model, state);
        EntityPoses.afterSetupAnim(model, state, false);
    }
}
