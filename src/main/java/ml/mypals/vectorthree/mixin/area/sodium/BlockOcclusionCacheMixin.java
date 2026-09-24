package ml.mypals.vectorthree.mixin.area.sodium;

import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public class BlockOcclusionCacheMixin {
    @Shadow protected BlockState state;
    @Shadow
    protected BlockPos pos;
    @Shadow protected BlockAndTintGetter level;
    @Inject(at = @At("HEAD"), method = "shouldDrawSide", remap = false, cancellable = true)
    private void vector3$shouldDrawSide(Direction facing, CallbackInfoReturnable<Boolean> cir) {
        BlockPos neighborPos = this.pos.relative(facing);
        boolean renderThis = !AreaSuppression.isSuppressed(this.pos);
        boolean renderNeighbor = !AreaSuppression.isSuppressed(neighborPos);
        if (renderThis != renderNeighbor) cir.setReturnValue(true);
    }
}

