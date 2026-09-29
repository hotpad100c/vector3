package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.fb.camera.dolly.DollyZoomCamera;
import ml.mypals.vectorthree.fb.camera.lookto.LookToCamera;
import ml.mypals.vectorthree.mc.vfx.ScreenVFXRenderer;
import ml.mypals.vectorthree.mc.pose.EntityPoses;
import ml.mypals.vectorthree.mc.light.LightRenderer;
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
        if (handler instanceof MinecraftKeyframeHandler) {
            ScreenVFXRenderer.begin();
            LightRenderer.begin();
            EntityPoses.begin();
        }
    }

    @Inject(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V", at = @At("RETURN"))
    private void vector3$finishLookTo(KeyframeHandler handler, float tick, long stamp, CallbackInfo ci) {
        DollyZoomCamera.finish(handler);
        LookToCamera.finish(handler);
        if (handler instanceof MinecraftKeyframeHandler) {
            ScreenVFXRenderer.finish();
            LightRenderer.finish();
            EntityPoses.finish();
        }
    }
}
