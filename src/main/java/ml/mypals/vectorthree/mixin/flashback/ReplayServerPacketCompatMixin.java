package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.exception.UnsupportedPacketException;
import com.moulberry.flashback.playback.ReplayServer;
import ml.mypals.vectorthree.flashback.PacketCompat;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Every replayed packet goes through one of these two handle calls; see PacketCompat.
@Mixin(value = ReplayServer.class, remap = false)
public class ReplayServerPacketCompatMixin {
    @WrapOperation(method = {"handleConfigurationPacket", "handleGamePacket"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void vector3$skipUnsupported(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        try {
            original.call(packet, listener);
        } catch (UnsupportedPacketException exception) {
            if (!PacketCompat.enabled()) throw exception;
            PacketCompat.skipped(packet, exception);
        }
    }
}
