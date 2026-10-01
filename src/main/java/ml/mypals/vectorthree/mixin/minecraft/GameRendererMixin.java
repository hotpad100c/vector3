package ml.mypals.vectorthree.mixin.minecraft;

import ml.mypals.vectorthree.core.port.FrameHooks;
import ml.mypals.vectorthree.mc.camera.EditorOverlays;
import ml.mypals.vectorthree.mc.camera.PreviewPass;
import com.llamalad7.mixinextras.sugar.Local;
import ml.mypals.vectorthree.mc.vfx.ScreenVFXRenderer;
import ml.mypals.vectorthree.mc.render.KitFeatureDispatcher;
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
        EditorOverlays.hideForExport();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void vector3$restoreOverlays(CallbackInfo ci) {
        EditorOverlays.restoreAfterExport();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void vector3$endKitFeatureFrame(CallbackInfo ci) {
        KitFeatureDispatcher.endFrame();
    }

    // Once the level is drawn, before the hand clears the depth.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
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
