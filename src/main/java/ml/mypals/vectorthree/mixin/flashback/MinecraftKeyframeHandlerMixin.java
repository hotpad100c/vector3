package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import ml.mypals.vectorthree.fb.camera.CameraPreview;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// With the camera preview open, the camera keyframes play in the preview and leave the main view alone.
@Mixin(value = MinecraftKeyframeHandler.class, remap = false)
public class MinecraftKeyframeHandlerMixin {
    @Inject(method = "applyCameraPosition", at = @At("HEAD"), cancellable = true)
    private void vector3$detachPosition(Vector3d position, double yaw, double pitch, double roll, CallbackInfo ci) {
        if (CameraPreview.detachesMainView()) ci.cancel();
    }

    @Inject(method = "applyFov", at = @At("HEAD"), cancellable = true)
    private void vector3$detachFov(float fov, CallbackInfo ci) {
        if (CameraPreview.detachesMainView()) ci.cancel();
    }

    @Inject(method = "applyCameraShake", at = @At("HEAD"), cancellable = true)
    private void vector3$detachShake(float xFrequency, float xAmplitude, float yFrequency, float yAmplitude, CallbackInfo ci) {
        if (!CameraPreview.detachesMainView()) return;
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) editorState.replayVisuals.overrideCameraShake = false;
        ci.cancel();
    }
}
