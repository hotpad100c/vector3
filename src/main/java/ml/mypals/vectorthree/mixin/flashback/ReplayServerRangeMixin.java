package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.flashback.PlaybackRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReplayServer.class, remap = false)
public abstract class ReplayServerRangeMixin {
    @Shadow public volatile boolean replayPaused;
    @Shadow public abstract int getReplayTick();
    @Shadow public abstract void goToReplayTick(int tick);
    @Shadow public abstract EditorState getEditorState();

    @Inject(method = "handleNextTick", at = @At("HEAD"), cancellable = true)
    private void vector3$loopPlaybackRange(CallbackInfo ci) {
        if (replayPaused) return;
        EditorState state = getEditorState();
        if (state == null) return;
        long stamp = state.acquireRead();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            if (!PlaybackRange.enabled(scene)) return;
            int in = PlaybackRange.in(scene), out = PlaybackRange.out(scene);
            if (in >= 0 && out > in && getReplayTick() >= out) {
                goToReplayTick(in);
                ci.cancel();
            }
        } finally {
            state.release(stamp);
        }
    }
}
