package ml.mypals.vectorthree.mixin.minecraft.light;

import ml.mypals.vectorthree.mc.light.AlbedoCapture;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererAlbedoMixin {
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void vector3$beginAlbedo(CallbackInfo ci) {
        AlbedoCapture.begin();
    }

    @Inject(method = "submitFeatures", at = @At("RETURN"))
    private void vector3$endAlbedo(CallbackInfo ci) {
        AlbedoCapture.end();
    }
}
