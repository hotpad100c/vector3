package ml.mypals.vectorthree.mixin.minecraft;

import ml.mypals.vectorthree.core.port.FrameHooks;
import ml.mypals.vectorthree.mc.camera.PreviewPass;
import com.llamalad7.mixinextras.sugar.Local;
import ml.mypals.vectorthree.mc.fade.ScreenVFXRenderer;
import ml.mypals.vectorthree.mc.render.ScreenLayer;
import ml.mypals.vectorthree.mc.shape.particle.ParticleEmitters;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void vector3$cameraPreview(CallbackInfo ci) {
        ParticleEmitters.frame();
        PreviewPass.bind((GameRenderer) (Object) this);
        FrameHooks.runBeforeFrame();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
    private void vector3$captureDepth(CallbackInfo ci, @Local Matrix4f projectionMatrix) {
        ScreenVFXRenderer.captureDepth(projectionMatrix);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void vector3$fade(CallbackInfo ci) {
        ScreenVFXRenderer.renderEffects();
        ScreenLayer.render();
        ScreenVFXRenderer.renderFade();
    }
}
