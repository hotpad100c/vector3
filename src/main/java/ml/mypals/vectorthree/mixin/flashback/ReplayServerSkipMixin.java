package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.flashback.PlaybackRange;
import ml.mypals.vectorthree.flashback.loop.LoopKeyframeType;
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
    @Shadow private int currentTick;

    @Inject(method = "tickServer", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/playback/ReplayServer;tickRateManager()Lnet/minecraft/server/ServerTickRateManager;"))
    private void vector3$skipRanges(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        if (Flashback.EXPORT_JOB != null) return;
        EditorState editorState = ((ReplayServer) (Object) this).getEditorState();
        targetTick = LoopKeyframeType.resolve(LoopKeyframeType.scopes(editorState), currentTick, targetTick, replayPaused);
        if (replayPaused) return;
        targetTick = PlaybackRange.resolve(editorState, currentTick, targetTick);
        targetTick = SkipKeyframeType.resolve(SkipKeyframeType.scopes(editorState), targetTick);
    }
}
