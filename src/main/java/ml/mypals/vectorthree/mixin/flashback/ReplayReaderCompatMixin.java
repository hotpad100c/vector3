package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.playback.ReplayServer;
import ml.mypals.vectorthree.flashback.PacketCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Every replay action is read from its own slice here. In force-compatibility mode an action that fails, or that
// doesn't read all of its bytes (e.g. a packet recorded in a format this version decodes differently), is logged and
// its leftovers skipped, instead of Flashback aborting the replay.
@Mixin(value = ReplayReader.class, remap = false)
public class ReplayReaderCompatMixin {
    @WrapOperation(method = {"handleSnapshot", "handleNextAction"}, at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/action/Action;handle(Lcom/moulberry/flashback/playback/ReplayServer;Lnet/minecraft/network/RegistryFriendlyByteBuf;)V"))
    private void vector3$tolerateAction(Action action, ReplayServer server, RegistryFriendlyByteBuf buffer,
            Operation<Void> original) {
        if (!PacketCompat.enabled()) {
            original.call(action, server, buffer);
            return;
        }
        try {
            original.call(action, server, buffer);
        } catch (RuntimeException exception) {
            PacketCompat.skippedAction(action.name().toString(), exception);
        }
        int left = buffer.writerIndex() - buffer.readerIndex();
        if (left > 0) {
            PacketCompat.skippedAction(action.name() + " (" + left + " unread bytes)", null);
            buffer.readerIndex(buffer.writerIndex());
        }
    }
}
