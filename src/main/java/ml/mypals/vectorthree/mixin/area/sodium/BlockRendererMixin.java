package ml.mypals.vectorthree.mixin.area.sodium;

/*
 * Inert placeholder, ported from Lucidity's same-named file. See BlockOcclusionCacheMixin in this
 * package for the activation steps and why this can't compile without a Sodium dependency.
 *
 * Simplified from Lucidity's version for AreaShape's binary invisible/visible case (no fade-alpha
 * feature), so this only needs the geometry-skip half; face culling across the boundary is
 * BlockOcclusionCacheMixin's job, not this class's.
 *
 * Original target: net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer
 *
 * @Mixin(value = BlockRenderer.class, remap = false)
 * public class BlockRendererMixin {
 *     @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
 *     private void vector3$renderModel(BlockStateModel model, BlockState state, BlockPos pos,
 *             BlockPos origin, CallbackInfo ci) {
 *         if (AreaSuppression.isSuppressed(pos)) ci.cancel();
 *     }
 * }
 */
