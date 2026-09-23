package ml.mypals.vectorthree.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import ml.mypals.vectorthree.Vector3;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;

import java.util.Objects;


public final class IrisBypassTarget {
    public static boolean debugShowOnly = false;

    private static RenderTarget target;
    private static boolean preparedThisFrame;
    private static boolean usedThisFrame;
    private static int redirectCallsThisFrame;
    private static int lastLoggedRedirectCalls = -1;
    private static final StringBuilder redirectLabelsThisFrame = new StringBuilder();
    private static String lastLoggedRedirectLabels = "";

    private static int bypassDepth;
    private static boolean bypassApplied;
    private static boolean bypassSaved;
    private static int featureRouteDepth;

    private IrisBypassTarget() {}

    public static boolean isActive() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static void beginIrisBypass() {
        if (bypassDepth++ == 0) {
            bypassApplied = isActive();
            if (bypassApplied) {
                bypassSaved = ImmediateState.bypass;
                ImmediateState.bypass = true;
            }
        }
    }

    public static void endIrisBypass() {
        if (bypassDepth > 0 && --bypassDepth == 0 && bypassApplied) {
            ImmediateState.bypass = bypassSaved;
            bypassApplied = false;
        }
    }

    public static boolean isBypassing() {
        return bypassDepth > 0 && bypassApplied;
    }

    public static RenderTarget targetFor(RenderTarget main, String label) {
        return isBypassing() ? prepareForDraw(label) : main;
    }

    public static void renderFeatures(Runnable draw) {
        beginIrisBypass();
        featureRouteDepth++;
        try {
            draw.run();
        } finally {
            featureRouteDepth--;
            endIrisBypass();
        }
    }

    public static boolean isRoutingFeatures() {
        return featureRouteDepth > 0 && isBypassing();
    }

    public static RenderTarget prepareForDraw() {
        return prepareForDraw("?");
    }

    public static RenderTarget prepareForDraw(String label) {
        ensureTarget();
        if (debugShowOnly) {
            if (redirectLabelsThisFrame.length() > 0) redirectLabelsThisFrame.append(',');
            redirectLabelsThisFrame.append(label);
        }
        if (!preparedThisFrame) {

            Vector4f clearColor = debugShowOnly ? new Vector4f(1, 0, 1, 1) : new Vector4f(0, 0, 0, 0);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), clearColor, target.getDepthTexture(), 0.0);
            target.copyDepthFrom(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            preparedThisFrame = true;
        }
        usedThisFrame = true;
        redirectCallsThisFrame++;
        return target;
    }

    public static void blitToMain() {
        String labels = redirectLabelsThisFrame.toString();
        if (debugShowOnly && (redirectCallsThisFrame != lastLoggedRedirectCalls || !labels.equals(lastLoggedRedirectLabels))) {
            lastLoggedRedirectCalls = redirectCallsThisFrame;
            lastLoggedRedirectLabels = labels;
            Vector3.LOGGER.info("[IrisBypassTarget] redirected {} time(s) this frame (usedThisFrame={}, sources=[{}])",
                    redirectCallsThisFrame, usedThisFrame, labels);
        }
        redirectLabelsThisFrame.setLength(0);
        if (target != null && usedThisFrame) {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (debugShowOnly) {
                main.copyColorFrom(target);
            } else {
                assert main.getColorTextureView() != null;
                target.blitAndBlendToTexture(main.getColorTextureView(),
                        Objects.requireNonNull(main.hasDepth() ? main.getDepthTextureView() : null));
            }
        }
        preparedThisFrame = false;
        usedThisFrame = false;
        redirectCallsThisFrame = 0;
    }

    private static void ensureTarget() {
        Window window = Minecraft.getInstance().getWindow();
        int width = Math.max(1, window.getWidth());
        int height = Math.max(1, window.getHeight());
        if (target == null) {
            target = new TextureTarget("vector3_iris_bypass", width, height,
                    GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
    }
}
