package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.camera.dolly.DollyZoomCamera;
import ml.mypals.vectorthree.camera.lookto.LookToCamera;
import ml.mypals.vectorthree.flashback.fade.ScreenVFXRenderer;
import ml.mypals.vectorthree.flashback.pose.EntityPoses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EditorState.class, remap = false)
public class EditorStateLookToMixin {
    @Inject(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V", at = @At("HEAD"))
    private void vector3$beginLookTo(KeyframeHandler handler, float tick, long stamp, CallbackInfo ci) {
        DollyZoomCamera.begin(handler);
        LookToCamera.begin(handler);
        ScreenVFXRenderer.begin(handler);
        EntityPoses.begin(handler);
    }

    @Inject(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V", at = @At("RETURN"))
    private void vector3$finishLookTo(KeyframeHandler handler, float tick, long stamp, CallbackInfo ci) {
        DollyZoomCamera.finish(handler);
        LookToCamera.finish(handler);
        ScreenVFXRenderer.finish(handler);
        EntityPoses.finish(handler);
    }
}
