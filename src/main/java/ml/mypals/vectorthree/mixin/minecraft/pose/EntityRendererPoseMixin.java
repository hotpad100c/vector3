package ml.mypals.vectorthree.mixin.minecraft.pose;

import ml.mypals.vectorthree.mc.pose.EntityPoses;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Every renderer's extraction goes through here (subclasses call super), so every entity's state carries its poses.
@Mixin(EntityRenderer.class)
public class EntityRendererPoseMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"))
    private void vector3$attachPose(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        EntityPoses.attach(entity, state);
    }
}
