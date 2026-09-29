package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.core.clips.TimeMap;
import ml.mypals.vectorthree.fb.clips.ProjectClock;
import ml.mypals.vectorthree.fb.timeline.PlaybackRange;
import org.spongepowered.asm.mixin.Unique;
import ml.mypals.vectorthree.fb.loop.LoopKeyframeType;
import ml.mypals.vectorthree.fb.skip.SkipKeyframeType;
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
        if (ProjectClock.active()) {
            vector3$projectPlayback(editorState);
            return;
        }
        targetTick = LoopKeyframeType.resolve(LoopKeyframeType.scopes(editorState), currentTick, targetTick, replayPaused);
        if (replayPaused) return;
        targetTick = PlaybackRange.resolve(editorState, currentTick, targetTick);
        targetTick = SkipKeyframeType.resolve(SkipKeyframeType.scopes(editorState), targetTick);
    }

    // The project clock advances, loops and skips in project ticks, then the archive is pointed at the tick it shows.
    // In a gap that tick does not change, so the world stands still while the timeline keeps running.
    @Unique
    private void vector3$projectPlayback(EditorState editorState) {
        TimeMap map = ProjectClock.map();
        int last = ProjectClock.lastTick(), project = ProjectClock.tick();
        boolean playing = ProjectClock.playing();
        if (playing && project < map.end()) project++;
        project = LoopKeyframeType.resolve(LoopKeyframeType.scopes(editorState), last, project, replayPaused);
        if (!replayPaused) {
            project = PlaybackRange.resolve(editorState, last, project);
            project = SkipKeyframeType.resolve(SkipKeyframeType.scopes(editorState), project);
        }
        if (playing) replayPaused = project >= map.end();
        if (playing && project > map.end()) project = map.end();
        ProjectClock.set(project);
        targetTick = map.physical(project);
    }
}
