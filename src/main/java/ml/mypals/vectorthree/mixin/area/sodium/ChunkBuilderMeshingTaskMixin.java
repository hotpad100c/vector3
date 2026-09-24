package ml.mypals.vectorthree.mixin.area.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;


@Pseudo
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public abstract class ChunkBuilderMeshingTaskMixin {
    @WrapOperation(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
                    + "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)"
                    + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/DirectionalVisGraph;setOpaque(III)V"),
            remap = false)
    private void vector3$setOpaque(DirectionalVisGraph instance, int x, int y, int z, Operation<Void> original) {
        if (!AreaSuppression.isSuppressed(new BlockPos(x,y,z))) original.call(instance, x,y,z);
    }
}

