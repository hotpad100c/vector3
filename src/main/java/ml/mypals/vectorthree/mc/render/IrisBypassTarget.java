package ml.mypals.vectorthree.mc.render;

import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.mc.compat.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;

import java.util.Objects;


public final class IrisBypassTarget {
    public static boolean debugShowOnly = false;
    /** Shape#customData key: true for a vanilla-content shape (item/block/entity) drawn in the bypass pass. */
    public static final String SHAPE_FLAG = "vector3:bypass_shaders";

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
    private static boolean mainDepthChanged;
    private static RenderPipeline depthMergePipeline;

    private IrisBypassTarget() {}

    // The UI layer is drawn after the level, where the shader pack no longer applies.
    public static boolean isActive() {
        return IrisCompat.isPackInUse() && !ScreenLayer.isRendering();
    }

    public static void beginIrisBypass() {
        if (bypassDepth++ == 0) {
            bypassApplied = isActive();
            if (bypassApplied) {
                bypassSaved = IrisCompat.bypass();
                IrisCompat.setBypass(true);
            }
        }
    }

    public static void endIrisBypass() {
        if (bypassDepth > 0 && --bypassDepth == 0 && bypassApplied) {
            IrisCompat.setBypass(bypassSaved);
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
            if (!redirectLabelsThisFrame.isEmpty()) redirectLabelsThisFrame.append(',');
            redirectLabelsThisFrame.append(label);
        }
        if (!preparedThisFrame) {

            Vector4f clearColor = debugShowOnly ? new Vector4f(1, 0, 1, 1) : new Vector4f(0, 0, 0, 0);
            assert target.getColorTexture() != null;
            assert target.getDepthTexture() != null;
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), clearColor, target.getDepthTexture(), 0.0);
            target.copyDepthFrom(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            preparedThisFrame = true;
            mainDepthChanged = false;
        } else if (mainDepthChanged) {
            mergeMainDepth();
            mainDepthChanged = false;
        }
        usedThisFrame = true;
        redirectCallsThisFrame++;
        return target;
    }

    public static void markMainDepthChanged() {
        if (preparedThisFrame) mainDepthChanged = true;
    }

    private static void mergeMainDepth() {
        if (depthMergePipeline == null) {
            depthMergePipeline = RenderPipelines.register(RenderPipeline.builder()
                    .withLocation(Mod.id("pipeline/bypass_depth_merge"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Mod.id("core/blit_depth"))
                    .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    // 26.2 render passes size themselves from the first color attachment, so the color target is
                    // attached and masked off rather than left out.
                    .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build());
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (!main.useDepth) return;
        assert target.getDepthTextureView() != null;
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "vector3_bypass_depth_merge")
                .withColorAttachment(target.getColorTextureView())
                .withDepthAttachment(target.getDepthTextureView(), OptionalDouble.empty())
                .withRenderArea(new RenderPass.RenderArea(0, 0, target.width, target.height));
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            pass.setPipeline(depthMergePipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", main.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    public static void blitToMain() {
        String labels = redirectLabelsThisFrame.toString();
        if (debugShowOnly && (redirectCallsThisFrame != lastLoggedRedirectCalls || !labels.equals(lastLoggedRedirectLabels))) {
            lastLoggedRedirectCalls = redirectCallsThisFrame;
            lastLoggedRedirectLabels = labels;
            Mod.LOGGER.info("[IrisBypassTarget] redirected {} time(s) this frame (usedThisFrame={}, sources=[{}])",
                    redirectCallsThisFrame, usedThisFrame, labels);
        }
        redirectLabelsThisFrame.setLength(0);
        if (target != null && usedThisFrame) {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (debugShowOnly) {
                RenderTargets.copyColor(target, main);
            } else {
                assert main.getColorTextureView() != null;
                target.blitAndBlendToTexture(main.getColorTextureView(),
                        Objects.requireNonNull(main.useDepth ? main.getDepthTextureView() : null));
            }
        }
        preparedThisFrame = false;
        mainDepthChanged = false;
        usedThisFrame = false;
        redirectCallsThisFrame = 0;
    }

    private static void ensureTarget() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = Math.max(1, main.width);
        int height = Math.max(1, main.height);
        if (target == null) {
            target = new TextureTarget("vector3_iris_bypass", width, height,
                    true, GpuFormat.RGBA8_UNORM);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
    }
}
