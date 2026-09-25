package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public class AreaLightEngineMixin {
    @Shadow @Final protected LightChunkGetter chunkSource;

    @Inject(method = "getState", at = @At("HEAD"), cancellable = true)
    private void vector3$suppressLight(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (AreaSuppression.isSuppressed(pos) && chunkSource.getLevel() instanceof Level level && level.isClientSide()) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
