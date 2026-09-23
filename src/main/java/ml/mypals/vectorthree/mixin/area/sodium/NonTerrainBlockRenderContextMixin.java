package ml.mypals.vectorthree.mixin.area.sodium;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.vectorthree.shape.AreaSuppression;
import net.caffeinemc.mods.sodium.client.render.frapi.render.NonTerrainBlockRenderContext;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = NonTerrainBlockRenderContext.class, remap = false)
public abstract class NonTerrainBlockRenderContextMixin {
    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void vector3$renderModel(QuadEmitter output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState blockState, BlockStateModel model, long seed, CallbackInfo ci) {
        if (AreaSuppression.isSuppressed(pos)) ci.cancel();
    }
}

