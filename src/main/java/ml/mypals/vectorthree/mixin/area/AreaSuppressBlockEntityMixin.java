package ml.mypals.vectorthree.mixin.area;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.vectorthree.shape.AreaSuppression;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class AreaSuppressBlockEntityMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void vector3$suppressArea(BlockEntityRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (AreaSuppression.isSuppressed(state.blockPos)) ci.cancel();
    }

    /**
     * Sodium never calls {@code submit} above at all — its own world renderer collects block
     * entities via {@code tryExtractRenderState} directly (SodiumWorldRenderer#extractBlockEntity),
     * feeding the result straight into LevelRenderState.blockEntityRenderStates. Without this,
     * an AreaShape's source block entities render normally whenever Sodium is active, regardless of
     * the submit-side suppression above. Returning null here matches the method's own "try" contract
     * for "nothing to render" (same as when it has no matching renderer).
     */
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void vector3$suppressAreaExtract(
            E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            boolean bl, CallbackInfoReturnable<S> cir) {
        if (AreaSuppression.isSuppressed(blockEntity.getBlockPos())) cir.setReturnValue(null);
    }
}
