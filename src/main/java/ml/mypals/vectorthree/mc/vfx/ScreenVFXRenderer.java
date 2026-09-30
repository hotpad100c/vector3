package ml.mypals.vectorthree.mc.vfx;

import ml.mypals.vectorthree.core.fade.ScreenVFX;
import ml.mypals.vectorthree.core.fade.effects.GodRaysSettings;

import com.mojang.blaze3d.pipeline.RenderTarget;
import ml.mypals.vectorthree.mc.vfx.effects.AutoExposureEffect;
import ml.mypals.vectorthree.mc.vfx.effects.BloomEffect;
import ml.mypals.vectorthree.mc.vfx.effects.BlurEffect;
import ml.mypals.vectorthree.mc.vfx.effects.ColorGradingEffect;
import ml.mypals.vectorthree.mc.vfx.effects.DepthOfFieldEffect;
import ml.mypals.vectorthree.mc.vfx.effects.EffectTextures;
import ml.mypals.vectorthree.mc.vfx.effects.FadeEffect;
import ml.mypals.vectorthree.mc.vfx.effects.FlareEffect;
import ml.mypals.vectorthree.mc.vfx.effects.GodRaysEffect;
import ml.mypals.vectorthree.mc.vfx.effects.GrainEffect;
import ml.mypals.vectorthree.mc.vfx.effects.LensEffect;
import ml.mypals.vectorthree.mc.vfx.effects.LutEffect;
import ml.mypals.vectorthree.mc.vfx.effects.MotionBlurEffect;
import ml.mypals.vectorthree.mc.vfx.effects.OcclusionEffect;
import ml.mypals.vectorthree.mc.vfx.effects.PaniniEffect;
import ml.mypals.vectorthree.mc.vfx.effects.PixelationEffect;
import ml.mypals.vectorthree.mc.vfx.effects.ReflectionEffect;
import ml.mypals.vectorthree.mc.vfx.effects.VignetteEffect;
import ml.mypals.vectorthree.core.port.Ports;
import ml.mypals.vectorthree.mc.camera.PreviewPass;
import ml.mypals.vectorthree.mc.light.LightRenderer;
import ml.mypals.vectorthree.mc.light.AlbedoCapture;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;

/** Collects active effect instances and runs their passes in timeline order. */
public final class ScreenVFXRenderer {
    private static final List<ScreenVFX> pending = new ArrayList<>();
    private static List<ScreenVFX> current = List.of();

    private ScreenVFXRenderer() {}

    public static void begin() {
        pending.clear();
    }

    public static void request(ScreenVFX value) { pending.add(value); }

    public static void finish() {
        current = List.copyOf(pending);
    }

    public static void clear() {
        pending.clear();
        current = List.of();
        EffectTextures.clear();
        AutoExposureEffect.clear();
        MotionBlurEffect.clear();
        ReflectionEffect.clear();
        LightRenderer.clear();
        AlbedoCapture.clear();
    }

    /** Called before the hand pass, which clears the main depth buffer. */
    public static void captureDepth(Matrix4fc projection) {
        if (Ports.view().mainViewDetached() || !LightRenderer.hasLights() && current.stream().noneMatch(ScreenVFXRenderer::usesDepth)) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (LightRenderer.hasLights()) AlbedoCapture.render(main);
        DepthOfFieldEffect.captureDepth(main, projection);
    }

    private static boolean usesDepth(ScreenVFX value) {
        return value.has(ScreenVFX.DOF) || value.has(ScreenVFX.FLARE) || value.has(ScreenVFX.GOD_RAYS)
                || value.has(ScreenVFX.MOTION_BLUR) || value.has(ScreenVFX.AO) || value.has(ScreenVFX.SSR);
    }

    public static void renderEffects() {
        try {
            if (current.stream().noneMatch(value -> value.has(ScreenVFX.AUTO_EXPOSURE)))
                AutoExposureEffect.clear();
            if (current.stream().noneMatch(value -> value.has(ScreenVFX.MOTION_BLUR)))
                MotionBlurEffect.clear();
            GodRaysSettings localRays = null;
            for (ScreenVFX value : current)
                if (value.has(ScreenVFX.GOD_RAYS) && value.additional().rays().localLights())
                    localRays = value.additional().rays();
            if (!Ports.view().mainViewDetached())
                LightRenderer.render(Minecraft.getInstance().gameRenderer.mainRenderTarget(), localRays);
            render();
        } finally {
            DepthOfFieldEffect.endFrame();
        }
    }

    private static void render() {
        if (Ports.view().mainViewDetached()) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return;
        boolean overlay = Ports.view().focusOverlayActive() && !PreviewPass.isRendering();
        for (ScreenVFX value : current) {
            for (String effect : value.effects()) render(main, value, effect, overlay);
        }
    }

    private static void render(RenderTarget main, ScreenVFX value, String effect, boolean overlay) {
        switch (effect) {
            case ScreenVFX.GRADE -> ColorGradingEffect.render(main, value.grading());
            case ScreenVFX.BLOOM -> BloomEffect.render(main, value.bloom());
            case ScreenVFX.DOF -> DepthOfFieldEffect.render(main, value, overlay);
            case ScreenVFX.VIGNETTE -> VignetteEffect.render(main, value);
            case ScreenVFX.LUT -> LutEffect.render(main, value);
            case ScreenVFX.GRAIN -> GrainEffect.render(main, value.grain());
            case ScreenVFX.PIXELATION -> PixelationEffect.render(main, value.pixelation());
            case ScreenVFX.LENS -> LensEffect.render(main, value.lens());
            case ScreenVFX.BLUR -> BlurEffect.render(main, value.additional().blur());
            case ScreenVFX.FLARE -> FlareEffect.render(main, value.additional().flare());
            case ScreenVFX.GOD_RAYS -> GodRaysEffect.render(main, value.additional().rays());
            case ScreenVFX.AUTO_EXPOSURE -> AutoExposureEffect.render(main, value.additional().exposure());
            case ScreenVFX.MOTION_BLUR -> MotionBlurEffect.render(main, value.additional().motion());
            case ScreenVFX.PANINI -> PaniniEffect.render(main, value.additional().panini());
            case ScreenVFX.AO -> OcclusionEffect.render(main, value.additional().ao());
            case ScreenVFX.SSR -> ReflectionEffect.render(main, value.additional().ssr());
            default -> { }
        }
    }

    public static void renderFade() {
        if (Ports.view().mainViewDetached()) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        for (ScreenVFX value : current)
            if (value.has(ScreenVFX.FADE)) FadeEffect.render(main, value);
    }
}
