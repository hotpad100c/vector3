package ml.mypals.vectorthree.mixin.flashback;

import ml.mypals.vectorthree.mc.camera.PreviewPass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.visuals.WorldRenderHook;
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
        if (PreviewPass.isRendering()) ci.cancel();
    }
}
