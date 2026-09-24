package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.camera.lookto.LookToCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the LookTo rotation after every track, so camera tracks can't overwrite it. */
@Mixin(value = EditorState.class, remap = false)
public class EditorStateLookToMixin {
    @Inject(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V", at = @At("HEAD"))
    private void vector3$beginLookTo(KeyframeHandler handler, float tick, long stamp, CallbackInfo ci) {
        LookToCamera.begin(handler);
    }

    @Inject(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V", at = @At("RETURN"))
    private void vector3$finishLookTo(KeyframeHandler handler, float tick, long stamp, CallbackInfo ci) {
        LookToCamera.finish(handler);
    }
}
