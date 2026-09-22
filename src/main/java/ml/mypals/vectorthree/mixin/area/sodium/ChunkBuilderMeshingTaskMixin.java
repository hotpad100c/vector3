package ml.mypals.vectorthree.mixin.area.sodium;

/*
 * Inert placeholder, ported from Lucidity's same-named file. See BlockOcclusionCacheMixin in this
 * package for the activation steps and why this can't compile without a Sodium dependency. Also needs
 * the `@Pseudo` annotation Lucidity used here (relaxes Mixin's signature verification against the real
 * target), since ChunkBuilderMeshingTask.execute(...)'s signature has drifted across Sodium releases —
 * requires the `org.spongepowered:mixin` `@Pseudo` support Lucidity depends on (MixinExtras too, for
 * @WrapOperation/@Local); check vector3's mixin tooling supports both before uncommenting.
 *
 * Sodium's own equivalent of vanilla's VisGraph.setOpaque gate — without this, a suppressed block
 * still marks its section-internal occlusion graph as opaque under Sodium, which can leave stray
 * internal culling artifacts even though it won't itself render.
 *
 * Original target: net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask
 *
 * @Pseudo
 * @Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
 * public abstract class ChunkBuilderMeshingTaskMixin {
 *     @WrapOperation(
 *             method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
 *                     + "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)"
 *                     + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
 *             at = @At(value = "INVOKE",
 *                     target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"),
 *             remap = false)
 *     private void vector3$setOpaque(VisGraph instance, BlockPos pos, Operation<Void> original) {
 *         if (!AreaSuppression.isSuppressed(pos)) original.call(instance, pos);
 *     }
 * }
 */
