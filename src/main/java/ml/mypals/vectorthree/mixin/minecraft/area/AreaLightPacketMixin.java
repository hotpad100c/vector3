package ml.mypals.vectorthree.mixin.minecraft.area;

import ml.mypals.vectorthree.mc.shape.area.AreaSuppression;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class AreaLightPacketMixin {
    @Inject(method = "applyLightData", at = @At("RETURN"))
    private void vector3$relight(int x, int z, ClientboundLightUpdatePacketData data, boolean update, CallbackInfo ci) {
        AreaSuppression.relightChunk(x, z);
    }

    // A chunk that arrives with its light only has that light enabled here, after applyLightData: checks queued
    // before this can be dropped, leaving the replay's light (worked out with the hidden blocks still in place).
    @Inject(method = "enableChunkLight", at = @At("RETURN"))
    private void vector3$relightLoaded(LevelChunk chunk, int x, int z, CallbackInfo ci) {
        AreaSuppression.relightChunk(x, z);
    }
}
