package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.playback.ReplayServer;
import ml.mypals.vectorthree.fb.clips.ProjectClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

/**
 * With a project map, everything outside the server (timeline, keyframes, export) reads and writes project ticks:
 * the accessors answer with the project clock, and a jump to a project tick is turned into the archive tick it shows.
 */
@Mixin(value = ReplayServer.class, remap = false)
public abstract class ReplayServerProjectTimeMixin {
    @Shadow public volatile int jumpToTick;
    @Shadow public volatile boolean replayPaused;
    @Shadow private volatile int targetTick;
    @Shadow private int lastReplayTick;
    @Shadow private boolean isFrozen;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void vector3$projectJump(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (ProjectClock.isNewServer(this)) ProjectClock.set(targetTick);
        if (!ProjectClock.active()) {
            ProjectClock.set(targetTick);
            ProjectClock.beginTick(!replayPaused);
            return;
        }
        ProjectClock.beginTick(!replayPaused);
        if (jumpToTick >= 0) {
            ProjectClock.set(jumpToTick);
            jumpToTick = ProjectClock.map().physical(jumpToTick);
        }
    }

    @Inject(method = "getReplayTick", at = @At("HEAD"), cancellable = true)
    private void vector3$projectTick(CallbackInfoReturnable<Integer> cir) {
        if (ProjectClock.active()) cir.setReturnValue(ProjectClock.tick());
    }

    @Inject(method = "getTotalReplayTicks", at = @At("HEAD"), cancellable = true)
    private void vector3$projectTotal(CallbackInfoReturnable<Integer> cir) {
        if (ProjectClock.active()) cir.setReturnValue(ProjectClock.map().end());
    }

    // The fraction between two server ticks is the archive clock's own time-based one; only its base is replaced.
    @Inject(method = "getPartialReplayTick", at = @At("RETURN"), cancellable = true)
    private void vector3$projectPartial(CallbackInfoReturnable<Double> cir) {
        if (!ProjectClock.active()) return;
        if (replayPaused || isFrozen) {
            cir.setReturnValue((double) ProjectClock.tick());
            return;
        }
        double fraction = Math.clamp(cir.getReturnValueD() - lastReplayTick, 0.0, 1.0);
        cir.setReturnValue(ProjectClock.lastTick() + fraction);
    }
}
