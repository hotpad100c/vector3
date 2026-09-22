package ml.mypals.vectorthree.mixin.area.sodium;

/*
 * Inert placeholder, ported from Lucidity's same-named file. See BlockOcclusionCacheMixin in this
 * package for the activation steps and why this can't compile without a Sodium dependency.
 *
 * Fluid counterpart of BlockRendererMixin — same simplification (no fade-alpha, geometry skip only).
 *
 * Original target: net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer
 *
 * @Mixin(value = DefaultFluidRenderer.class, remap = false)
 * public class DefaultFluidRendererMixin {
 *     @Inject(method = "render", at = @At("HEAD"), cancellable = true)
 *     private void vector3$render(LevelSlice level, BlockState state, FluidState fluidState, BlockPos pos,
 *             BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder,
 *             Material material, ColorProvider<FluidState> colorProvider, TextureAtlasSprite[] sprites,
 *             CallbackInfo ci) {
 *         if (AreaSuppression.isSuppressed(pos)) ci.cancel();
 *     }
 * }
 */
