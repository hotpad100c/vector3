package ml.mypals.vectorthree.mixin.minecraft.iris.skyOverride;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import ml.mypals.vectorthree.core.port.Ports;


@Mixin(IrisRenderingPipeline.class)
public class IrisRenderingPipelineMixin {
    @ModifyExpressionValue(
            method = "beginLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/uniforms/CapturedRenderingState;getFogColor()Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private Vector3d compatibleirisskyoverride$overrideClearColour(Vector3d original) {
        var sky = Ports.view().skyOverride();
        if (sky == null) {
            return original;
        }
        return new Vector3d(sky.r(), sky.g(), sky.b());
    }
}
