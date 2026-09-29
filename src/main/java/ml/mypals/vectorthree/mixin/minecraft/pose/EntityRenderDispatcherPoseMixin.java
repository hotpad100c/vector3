package ml.mypals.vectorthree.mixin.minecraft.pose;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.vectorthree.mc.pose.EntityPoses;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks which entity is being submitted, so its first model submission can be told apart (see EntityPoses).
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherPoseMixin {
    @Unique private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V";

    @Inject(method = SUBMIT, at = @At("HEAD"))
    private void vector3$beginSubmit(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
            PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        Matrix4f origin = new Matrix4f(poseStack.last().pose()).translate((float) x, (float) y, (float) z);
        EntityPoses.beginEntitySubmit(state, ((EntityRenderDispatcher) (Object) this).getRenderer(state), origin, camera.pos);
    }

    @Inject(method = SUBMIT, at = @At("RETURN"))
    private void vector3$endSubmit(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
            PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        EntityPoses.endEntitySubmit();
    }
}
