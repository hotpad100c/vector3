package ml.mypals.vectorthree.mixin.pose;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.flashback.pose.EntityPoses;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.CameraPreview;
import ml.mypals.vectorthree.compat.IrisCompat;
import ml.mypals.vectorthree.render.ScreenLayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.UUID;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererPoseMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void vector3$attachPose(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        UUID identity = EntityPoses.poseIdentity(entity.getUUID());
        ((EntityPoses.Holder) state).vector3$setPoses(identity, EntityPoses.get(identity));
    }

    // The model's parts are only set up later, when the submit is drawn; for the gizmo's entity they are set up
    // here once more (as ModelFeatureRenderer will) to report where each part is.
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private void vector3$reportPartFrames(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack,
            RenderType renderType, int light, int overlay, int tint, UvMapping uvMapping, int outline,
            Operation<Void> original, @Local(argsOnly = true) CameraRenderState camera) {
        UUID entity = state instanceof EntityPoses.Holder holder ? holder.vector3$poseEntity() : null;
        boolean gizmo = entity != null && entity.equals(Vector3.POSE_GIZMO.entity());
        // Not from the preview or shadow passes: their matrices aren't relative to the main camera.
        if (entity != null && !CameraPreview.isRendering() && !IrisCompat.isRenderingShadowPass() && !ScreenLayer.isRendering()
                && (gizmo || EntityPoses.snapshotPending(entity))) {
            vector3$setupAnim(model, state);
            EntityPoses.afterSetupAnim(model, state, true);
            if (gizmo) Vector3.POSE_GIZMO.reportFrames(model, new Matrix4f(poseStack.last().pose()), camera.pos);
        }
        original.call(collector, model, state, poseStack, renderType, light, overlay, tint, uvMapping, outline);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <S> void vector3$setupAnim(Model<S> model, Object state) {
        model.setupAnim((S) state);
    }

    // The layers (held items, ...) read the entity model's parts right after this, so they follow the pose.
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"))
    private void vector3$poseModel(EntityModel<?> model, Object state, Operation<Void> original) {
        original.call(model, state);
        EntityPoses.afterSetupAnim(model, state, false);
    }
}
