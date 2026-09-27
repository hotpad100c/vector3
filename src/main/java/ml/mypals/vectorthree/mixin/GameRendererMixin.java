package ml.mypals.vectorthree.mixin;

import ml.mypals.vectorthree.camera.CameraPreview;
import ml.mypals.vectorthree.flashback.fade.FadeOverlay;
import ml.mypals.vectorthree.shape.particle.ParticleEmitters;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void vector3$cameraPreview(CallbackInfo ci) {
        ParticleEmitters.frame();
        CameraPreview.beforeFrame((GameRenderer) (Object) this);
    }

    // After the level and its post effects, before the GUI: the fade covers exactly what gets exported.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void vector3$fade(CallbackInfo ci) {
        FadeOverlay.render();
    }
}
