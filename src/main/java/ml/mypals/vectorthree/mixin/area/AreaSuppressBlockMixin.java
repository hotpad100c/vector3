package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ModelBlockRenderer.class)
public class AreaSuppressBlockMixin {
    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void vector3$suppressArea(BlockQuadOutput output, float x, float y, float z,
                                      BlockAndTintGetter level, BlockPos pos, BlockState blockState, BlockStateModel model, long seed,
                                      CallbackInfo ci) {
        if (AreaSuppression.isSuppressed(pos)) ci.cancel();
    }
}
