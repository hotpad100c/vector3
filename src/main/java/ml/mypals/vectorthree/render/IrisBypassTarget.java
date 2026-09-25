package ml.mypals.vectorthree.render;

import ml.mypals.vectorthree.compat.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.OptionalDouble;
import ml.mypals.vectorthree.Vector3;
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

    public static boolean isActive() {
        return IrisCompat.isPackInUse();
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
                    .withLocation(Vector3.id("pipeline/bypass_depth_merge"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader("core/blit_depth")
                    .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build());
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (!main.hasDepth()) return;
        assert target.getDepthTextureView() != null;
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> "vector3_bypass_depth_merge")
                .withDepthAttachment(target.getDepthTextureView(), OptionalDouble.empty())
                .withRenderArea(new RenderPass.RenderArea(0, 0, target.width, target.height))
                .build();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(depthMergePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
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
                    GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
    }
}
