package ml.mypals.vectorthree.mixin.rrk;

import ml.mypals.ryansrenderingkit.render.MainRender;
import ml.mypals.vectorthree.render.ScreenLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MainRender.class, remap = false)
public class MainRenderScreenLayerMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private static void vector3$hideScreenLayer(CallbackInfo ci) {
        ScreenLayer.beginWorldPass();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void vector3$restoreScreenLayer(CallbackInfo ci) {
        ScreenLayer.endWorldPass();
    }
}
