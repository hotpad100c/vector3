package ml.mypals.vectorthree.mixin.flashback;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.visuals.WorldRenderHook;
import ml.mypals.vectorthree.camera.CameraPreview;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Flashback's editor overlays (camera path, markers) stay out of the camera preview, as they do in exports.
@Mixin(value = WorldRenderHook.class, remap = false)
public class WorldRenderHookPreviewMixin {
    @Inject(method = "renderHook", at = @At("HEAD"), cancellable = true)
    private static void vector3$hideInPreview(PoseStack poseStack, CameraRenderState camera, CallbackInfo ci) {
        if (CameraPreview.isRendering()) ci.cancel();
    }
}
