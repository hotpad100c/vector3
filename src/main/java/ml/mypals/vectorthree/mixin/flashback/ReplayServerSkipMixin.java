package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import ml.mypals.vectorthree.flashback.skip.SkipKeyframeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(value = ReplayServer.class, remap = false)
public abstract class ReplayServerSkipMixin {
    @Shadow private volatile int targetTick;
    @Shadow public volatile boolean replayPaused;

    @Inject(method = "tickServer", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/playback/ReplayServer;tickRateManager()Lnet/minecraft/server/ServerTickRateManager;"))
    private void vector3$skipRanges(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        if (replayPaused || Flashback.EXPORT_JOB != null) return;
        targetTick = SkipKeyframeType.resolve(SkipKeyframeType.scopes(((ReplayServer) (Object) this).getEditorState()), targetTick);
    }
}
