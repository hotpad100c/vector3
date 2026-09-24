package ml.mypals.vectorthree.mixin.area.sodium;

import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public class BlockRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void vector3$renderModel(BlockStateModel model, BlockState state, BlockPos pos,
            BlockPos origin, CallbackInfo ci) {
        if (AreaSuppression.isSuppressed(pos)) ci.cancel();
    }
}

