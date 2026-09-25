package ml.mypals.vectorthree.mixin.area;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSkyLightSources.class)
public class AreaSkyLightSourcesMixin {
    @Unique
    private static boolean vector3$suppressed(Object chunk, int x, int y, int z) {
        if (!(chunk instanceof LevelChunk levelChunk) || !levelChunk.getLevel().isClientSide()) return false;
        return AreaSuppression.isSuppressed(new BlockPos(levelChunk.getPos().getMinBlockX() + (x & 15), y,
                levelChunk.getPos().getMinBlockZ() + (z & 15)));
    }

    @WrapOperation(method = "findLowestSourceY", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState vector3$suppressSection(LevelChunkSection section, int x, int y, int z, Operation<BlockState> original,
            @Local(argsOnly = true) ChunkAccess chunk, @Local(name = "sectionIndex") int sectionIndex) {
        int worldY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex)) + y;
        return vector3$suppressed(chunk, x, worldY, z) ? Blocks.AIR.defaultBlockState() : original.call(section, x, y, z);
    }

    @WrapOperation(method = {"update", "findLowestSourceBelow"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState vector3$suppressGetter(BlockGetter level, BlockPos pos, Operation<BlockState> original) {
        return vector3$suppressed(level, pos.getX(), pos.getY(), pos.getZ()) ? Blocks.AIR.defaultBlockState() : original.call(level, pos);
    }
}
