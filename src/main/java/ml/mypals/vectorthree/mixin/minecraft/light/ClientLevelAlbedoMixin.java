package ml.mypals.vectorthree.mixin.minecraft.light;

import ml.mypals.vectorthree.mc.light.AlbedoCapture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelAlbedoMixin {
    @Inject(method = "setBlocksDirty", at = @At("RETURN"))
    private void vector3$blockAlbedoDirty(BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
        AlbedoCapture.blockChanged(pos);
    }

    @Inject(method = "onChunkLoaded", at = @At("RETURN"))
    private void vector3$chunkAlbedoLoaded(ChunkPos pos, CallbackInfo ci) {
        AlbedoCapture.chunkChanged(pos.x(), pos.z());
    }

    @Inject(method = "unload", at = @At("RETURN"))
    private void vector3$chunkAlbedoUnloaded(LevelChunk chunk, CallbackInfo ci) {
        AlbedoCapture.chunkChanged(chunk.getPos().x(), chunk.getPos().z());
    }
}
