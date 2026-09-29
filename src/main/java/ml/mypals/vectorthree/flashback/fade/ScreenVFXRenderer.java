package ml.mypals.vectorthree.flashback.fade;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import ml.mypals.vectorthree.flashback.fade.effects.*;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.CameraPreview;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;

import static ml.mypals.vectorthree.camera.CameraPreview.detachesMainView;

/** Collects active effect instances and runs their passes in timeline order. */
public final class ScreenVFXRenderer {
    private static final List<ScreenVFX> pending = new ArrayList<>();
    private static List<ScreenVFX> current = List.of();

    private ScreenVFXRenderer() {}

    public static void begin(KeyframeHandler handler) {
        if (handler instanceof MinecraftKeyframeHandler) pending.clear();
    }

    public static void request(ScreenVFX value) { pending.add(value); }

    public static void finish(KeyframeHandler handler) {
        if (handler instanceof MinecraftKeyframeHandler) current = List.copyOf(pending);
    }

    public static void clear() {
        pending.clear();
        current = List.of();
        EffectTextures.clear();
        AutoExposureEffect.clear();
        MotionBlurEffect.clear();
    }

    /** Called before the hand pass, which clears the main depth buffer. */
    public static void captureDepth(Matrix4fc projection) {
        if (detachesMainView() || current.stream().noneMatch(ScreenVFXRenderer::usesDepth)) return;
        DepthOfFieldEffect.captureDepth(Minecraft.getInstance().gameRenderer.mainRenderTarget(), projection);
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
            render();
        } finally {
            DepthOfFieldEffect.endFrame();
        }
    }

    private static void render() {
        if (detachesMainView()) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return;
        boolean overlay = Vector3.FOCUS_GIZMO.overlayActive() && !CameraPreview.isRendering();
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
        if (detachesMainView()) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        for (ScreenVFX value : current)
            if (value.has(ScreenVFX.FADE)) FadeEffect.render(main, value);
    }
}
