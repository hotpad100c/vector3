package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.AreaSuppression;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public class AreaSuppressFluidMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void vector3$suppressArea(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
            BlockState state, FluidState fluidState, CallbackInfo ci) {
        if (AreaSuppression.isSuppressed(pos)) ci.cancel();
    }
}
