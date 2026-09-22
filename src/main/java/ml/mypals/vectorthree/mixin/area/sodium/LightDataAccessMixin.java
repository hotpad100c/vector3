package ml.mypals.vectorthree.mixin.area.sodium;

/*
 * Inert placeholder, ported from Lucidity's mixin/features/selectiveRendering/sodium/light package.
 * See BlockOcclusionCacheMixin in this package for the activation steps and why this can't compile
 * without a Sodium dependency.
 *
 * Makes Sodium's own AO/light sampling treat a suppressed position as air, the same narrow "fake air"
 * trick vanilla needs its own separate light-engine mixin for (not yet ported either — the vanilla
 * light path hasn't come up as visibly broken yet, so it's been deferred; do that one first since it
 * doesn't need an external dependency).
 *
 * Original target: net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess
 *
 * @Mixin(value = LightDataAccess.class, remap = false)
 * public class LightDataAccessMixin {
 *     @WrapOperation(method = "compute", at = @At(value = "INVOKE",
 *             target = "Lnet/minecraft/world/level/BlockAndTintGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
 *     private BlockState vector3$getBlockState(BlockAndTintGetter world, BlockPos pos, Operation<BlockState> original) {
 *         return AreaSuppression.isSuppressed(pos) ? Blocks.AIR.defaultBlockState() : original.call(world, pos);
 *     }
 * }
 */
