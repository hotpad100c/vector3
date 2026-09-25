package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.area.AreaSuppression;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
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
}
