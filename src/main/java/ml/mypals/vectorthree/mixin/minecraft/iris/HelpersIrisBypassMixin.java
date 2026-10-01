package ml.mypals.vectorthree.mixin.minecraft.iris;

import ml.mypals.vectorthree.mc.compat.IrisCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.mc.render.IrisBypassTarget;
import ml.mypals.vectorthree.mc.render.KitFeatureDispatcher;
import ml.mypals.vectorthree.mc.render.RenderTargets;
import ml.mypals.vectorthree.mc.render.TextGlow;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Helpers#renderFeatures — RRK's feature-dispatcher draw used by the EmptyMesh shapes — draws into the main
 * target, on KitFeatureDispatcher's dispatcher rather than the game's. Only calls wrapped in
 * IrisBypassTarget#renderFeatures (or TextGlow's routing) are sent elsewhere, through
 * RenderSystem's output override, so other callers keep their behavior.
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

    @Unique
    private static RenderTarget vector3$routedTarget() {
        if (TextGlow.isRouting()) return TextGlow.prepareForDraw();
        return IrisBypassTarget.isRoutingFeatures() ? IrisBypassTarget.prepareForDraw("Features") : null;
    }

    // 26.2's renderAllFeatures builds the frame (prepareFrame) and draws it in one call.
    @WrapOperation(method = "renderFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private static void vector3$routeFeatures(FeatureRenderDispatcher gameDispatcher, SubmitNodeStorage submits,
            Operation<Void> original) {
        FeatureRenderDispatcher dispatcher = KitFeatureDispatcher.get();
        RenderTarget target = vector3$routedTarget();
        if (!vector3$vanillaFormats()) {
            RenderTargets.drawInto(target, () -> original.call(dispatcher, submits));
            return;
        }
        boolean renderingLevel = IrisCompat.isRenderingLevel();
        boolean skip = IrisCompat.skipExtension();
        IrisCompat.setRenderingLevel(false);
        try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(submits)) {
            IrisCompat.setRenderingLevel(renderingLevel);
            IrisCompat.setSkipExtension(true);
            RenderTargets.drawInto(target, () -> {
                frame.executeSolid();
                frame.executeTranslucent();
                frame.executeTranslucentAfterTerrain();
                frame.executeAlwaysOnTop();
            });
        } finally {
            IrisCompat.setRenderingLevel(renderingLevel);
            IrisCompat.setSkipExtension(skip);
        }
    }
}
