package ml.mypals.vectorthree.mixin.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.BufferedVertexBuilder;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = BufferedVertexBuilder.class, remap = false)
public class BufferedVertexBuilderIrisBypassMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void vector3$beginIrisBypass(CallbackInfo ci) {
        IrisBypassTarget.beginIrisBypass();
    }

    @Inject(method = "draw", at = @At("RETURN"))
    private void vector3$endIrisBypass(CallbackInfo ci) {
        IrisBypassTarget.endIrisBypass();
    }

    @Redirect(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget vector3$redirectToBypassTarget(GameRenderer gameRenderer) {
        return IrisBypassTarget.targetFor(gameRenderer.mainRenderTarget(), "BufferedVertexBuilder");
    }
}
