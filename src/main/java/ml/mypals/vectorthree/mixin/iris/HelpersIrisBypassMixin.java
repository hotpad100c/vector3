package ml.mypals.vectorthree.mixin.iris;

import ml.mypals.vectorthree.compat.IrisCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import ml.mypals.vectorthree.render.TextGlow;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Helpers#renderFeatures — RRK's feature-dispatcher draw used by the EmptyMesh shapes — hardcodes
 * {@code gameRenderer.mainRenderTarget()}. Only calls wrapped in IrisBypassTarget#renderFeatures are
 * redirected, so other callers keep their behavior.
 * <p>
 * Routed draws also pin vanilla vertex layouts. With a pack active Iris widens BLOCK/ENTITY/glyph
 * vertices, deciding separately when buffers are built and when they're bound; with its shader
 * bypassed, the widened data then desyncs from vanilla translucent sorting and the geometry vanishes or
 * breaks up. Every widening check requires ImmediateState.isRenderingLevel, so it's cleared while the
 * frame is built; the draw keeps it (Iris's reversed-Z undo keys off it) and sets skipExtension, the
 * only check the draw-time binding makes that Iris doesn't reset.
 */
@Mixin(value = Helpers.class, remap = false)
public class HelpersIrisBypassMixin {
    @Unique
    private static boolean vector3$vanillaFormats() {
        return IrisBypassTarget.isRoutingFeatures() || TextGlow.isRouting() && IrisBypassTarget.isBypassing();
    }

    @Redirect(method = "renderFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private static RenderTarget vector3$redirectToBypassTarget(GameRenderer gameRenderer) {
        if (TextGlow.isRouting()) return TextGlow.prepareForDraw();
        return IrisBypassTarget.isRoutingFeatures()
                ? IrisBypassTarget.prepareForDraw("Features")
                : gameRenderer.mainRenderTarget();
    }

    @WrapOperation(method = "renderFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private static FeatureRenderDispatcher.PreparedFrame vector3$buildWithVanillaFormats(FeatureRenderDispatcher dispatcher,
            SubmitNodeStorage submits, Operation<FeatureRenderDispatcher.PreparedFrame> original) {
        if (!vector3$vanillaFormats()) return original.call(dispatcher, submits);
        boolean renderingLevel = IrisCompat.isRenderingLevel();
        IrisCompat.setRenderingLevel(false);
        try {
            return original.call(dispatcher, submits);
        } finally {
            IrisCompat.setRenderingLevel(renderingLevel);
        }
    }

    @WrapOperation(method = "renderFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"))
    private static void vector3$drawWithVanillaFormats(RenderPass pass, FeatureRenderDispatcher.PreparedFrame frame,
            Operation<Void> original) {
        if (!vector3$vanillaFormats()) {
            original.call(pass, frame);
            return;
        }
        boolean skip = IrisCompat.skipExtension();
        IrisCompat.setSkipExtension(true);
        try {
            original.call(pass, frame);
        } finally {
            IrisCompat.setSkipExtension(skip);
        }
    }
}
