package ml.mypals.vectorthree.mixin.area.sodium;

/*
 * Inert placeholder, ported from Lucidity's same-named file. See BlockOcclusionCacheMixin in this
 * package for the activation steps and why this can't compile without a Sodium dependency.
 *
 * Covers blocks rendered through Sodium's Indigo-compatible (Fabric Rendering API) path outside the
 * normal terrain mesh — Sodium's equivalent of the vanilla+Indigo case AreaSuppressRenderSectionRegionMixin
 * already handles for the terrain path. Simplified for the binary invisible/visible case (geometry skip
 * only, no fade-alpha).
 *
 * Original target: net.caffeinemc.mods.sodium.client.render.frapi.render.NonTerrainBlockRenderContext
 *
 * @Mixin(value = NonTerrainBlockRenderContext.class, remap = false)
 * public abstract class NonTerrainBlockRenderContextMixin {
 *     @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
 *     private void vector3$renderModel(BlockAndTintGetter blockView, BlockColors blockColors,
 *             BlockStateModel model, BlockState state, BlockPos pos, PoseStack poseStack,
 *             BlockVertexConsumerProvider buffer, boolean cull, long seed, int overlay, CallbackInfo ci) {
 *         if (AreaSuppression.isSuppressed(pos)) ci.cancel();
 *     }
 * }
 */
