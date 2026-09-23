package ml.mypals.vectorthree.mixin.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(value = Helpers.class, remap = false)
public class HelpersIrisBypassMixin {
    @Redirect(method = "renderFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private static RenderTarget vector3$redirectToBypassTarget(GameRenderer gameRenderer) {
        return IrisBypassTarget.isRoutingFeatures()
                ? IrisBypassTarget.prepareForDraw("Features")
                : gameRenderer.mainRenderTarget();
    }
}
