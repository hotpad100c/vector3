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
    }

    /** Called before the hand pass, which clears the main depth buffer. */
    public static void captureDepth(Matrix4fc projection) {
        if (detachesMainView() || current.stream().noneMatch(ScreenVFXRenderer::usesDepth)) return;
        DepthOfFieldEffect.captureDepth(Minecraft.getInstance().gameRenderer.mainRenderTarget(), projection);
    }

    private static boolean usesDepth(ScreenVFX value) {
        return value.effect().equals(ScreenVFX.DOF) || value.effect().equals(ScreenVFX.LEGACY) && value.dofStrength() > 0;
    }

    public static void renderEffects() {
        try {
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
            switch (value.effect()) {
                case ScreenVFX.GRADE -> ColorGradingEffect.render(main, value.grading());
                case ScreenVFX.BLOOM -> BloomEffect.render(main, value.bloom());
                case ScreenVFX.DOF -> DepthOfFieldEffect.render(main, value, overlay);
                case ScreenVFX.VIGNETTE -> VignetteEffect.render(main, value);
                case ScreenVFX.LUT -> LutEffect.render(main, value);
                case ScreenVFX.LEGACY -> {
                    if (value.dofStrength() > 0) DepthOfFieldEffect.render(main, value, overlay);
                    ColorGradingEffect.render(main, value.grading());
                    VignetteEffect.render(main, value);
                    LutEffect.render(main, value);
                }
                default -> { }
            }
        }
    }

    public static void renderFade() {
        if (detachesMainView()) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        for (ScreenVFX value : current)
            if (value.applies(ScreenVFX.FADE)) FadeEffect.render(main, value);
    }
}
