package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.AreaSuppression;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(RenderSectionRegion.class)
public class AreaSuppressRenderSectionRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void vector3$suppressBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (AreaSuppression.isSuppressed(pos)) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void vector3$suppressFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (AreaSuppression.isSuppressed(pos)) cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void vector3$suppressBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (AreaSuppression.isSuppressed(pos)) cir.setReturnValue(null);
    }
}
