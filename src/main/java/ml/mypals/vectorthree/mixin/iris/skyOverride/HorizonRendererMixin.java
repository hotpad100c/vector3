package ml.mypals.vectorthree.mixin.iris.skyOverride;

import net.irisshaders.iris.pathways.HorizonRenderer;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static ml.mypals.vectorthree.Vector3.skyOverrideColor;

@Mixin(HorizonRenderer.class)
public class HorizonRendererMixin {
    @Inject(method = "renderHorizon", at = @At("HEAD"), cancellable = true, remap = false)
    private void compatibleirisskyoverride$cancelHorizon(Matrix4fc modelView, Matrix4fc projection,
                                                         Vector4f fogColor, CallbackInfo ci) {
        if (skyOverrideColor() != null) {
            ci.cancel();
        }
    }

}
