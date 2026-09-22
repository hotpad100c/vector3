package ml.mypals.vectorthree.mixin.area.sodium;

/*
 * Inert placeholder, ported from Lucidity (C:\coding\lucidity-1.21.11, same-named file under
 * mixin/features/selectiveRendering/sodium) for AreaShape's real-world suppression. NOT compiled or
 * registered: vector3 has no Sodium dependency (not even compileOnly) since Sodium doesn't yet build
 * for this MC version, and Sodium mixins reference Sodium classes directly (not string targets), so
 * this can't compile without it. AreaSuppressRenderSectionRegionMixin already gives vanilla + Indigo
 * both halves of correct face culling for free (see its javadoc) by making RenderSectionRegion report
 * air for suppressed positions — Sodium replaces that whole compile pipeline with its own classes, so
 * it needs this separate, explicit fix once it's available.
 *
 * To activate: add Sodium as a `modCompileOnly` Gradle dependency once a 26.3 build exists, uncomment
 * the class body below, verify the target class/method still matches that Sodium version (both the
 * package and the shouldDrawSide signature have drifted across Sodium releases before), and register
 * it in vector3.mixins.json with `defaultRequire: 0` (so a mismatch no-ops instead of crashing when
 * Sodium updates out from under this).
 *
 * Original target: net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext
 * (Sodium 0.8+; the class was called BlockOcclusionCache in older Sodium — hence the file's name).
 *
 * @Mixin(value = AbstractBlockRenderContext.class, remap = false)
 * public class BlockOcclusionCacheMixin {
 *     @Shadow protected BlockState state;
 *     @Shadow protected BlockPos pos;
 *     @Shadow protected BlockAndTintGetter level;
 *
 *     @Inject(at = @At("HEAD"), method = "shouldDrawSide", remap = false, cancellable = true)
 *     private void vector3$shouldDrawSide(Direction facing, CallbackInfoReturnable<Boolean> cir) {
 *         BlockPos neighborPos = this.pos.relative(facing);
 *         boolean renderThis = !AreaSuppression.isSuppressed(this.pos);
 *         boolean renderNeighbor = !AreaSuppression.isSuppressed(neighborPos);
 *         if (renderThis != renderNeighbor) cir.setReturnValue(true);
 *     }
 * }
 */
