package ml.mypals.vectorthree.mixin.iris.skyOverride;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static ml.mypals.vectorthree.Vector3.skyOverrideColor;


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
        float[] skyColour = skyOverrideColor();
        if (skyColour == null) {
            return original;
        }
        return new Vector3d(skyColour[0], skyColour[1], skyColour[2]);
    }
}
