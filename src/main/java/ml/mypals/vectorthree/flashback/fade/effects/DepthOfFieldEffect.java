package ml.mypals.vectorthree.flashback.fade.effects;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import ml.mypals.vectorthree.flashback.fade.ScreenVFX;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/**
 * Depth is captured into a linear view-distance texture before the hand pass clears it; the blur itself
 * runs later on the finished frame: half-resolution CoC prefilter, a bokeh gather, then a full-resolution composite.
 */
public final class DepthOfFieldEffect {
    private static final GpuFormat HALF_FORMAT = GpuFormat.RGBA16_FLOAT;
    private static RenderPipeline depthPipeline, prefilterPipeline, blurPipeline, compositePipeline;
    private static RenderTarget distance, prefiltered, blurred;
    private static GpuBuffer projectionSettings, settings;
    private static boolean depthReady;

    private DepthOfFieldEffect() {}

    public static void captureDepth(RenderTarget main, Matrix4fc projection) {
        if (main.getDepthTextureView() == null || main.width <= 0 || main.height <= 0) return;
        ensure(main);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 32)
                    .putVec4(projection.m22(), projection.m32(), projection.m23(), projection.m33())
                    .putVec4(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0, 0, 0, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(projectionSettings.slice(), data);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_dof_depth", distance.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(depthPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DepthSampler", main.getDepthTextureView(), ScreenPass.nearest());
            pass.setUniform("DOFProjection", projectionSettings);
            pass.draw(3, 1, 0, 0);
        }
        depthReady = true;
    }

    public static void endFrame() {
        depthReady = false;
    }

    public static void render(RenderTarget main, ScreenVFX value, boolean overlay) {
        if (!depthReady || distance.width != main.width || distance.height != main.height) return;
        float maxRadius = Math.clamp(value.dofStrength(), 0, 1) * main.height * 0.03f;
        boolean blur = maxRadius >= 0.5f;
        if (!blur && !overlay) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 48)
                    .putVec4(value.focusDistance(), value.focusRange() * 0.5f, value.dofMode(), maxRadius)
                    .putVec4(1f / main.width, 1f / main.height, 1f / prefiltered.width, 1f / prefiltered.height)
                    .putVec4(overlay ? 1 : 0, blur ? 1 : 0, 0, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        if (blur) {
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "vector3_dof_prefilter", prefiltered.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(prefilterPipeline));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
                pass.setUniform("DistanceSampler", distance.getColorTextureView(), ScreenPass.nearest());
                pass.setUniform("DOFSettings", settings);
                pass.draw(3, 1, 0, 0);
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "vector3_dof_blur", blurred.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(blurPipeline));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", prefiltered.getColorTextureView(), ScreenPass.linear());
                pass.setUniform("DOFSettings", settings);
                pass.draw(3, 1, 0, 0);
            }
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_dof_composite", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("BlurSampler", blurred.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("DistanceSampler", distance.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("DOFSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }

    private static void ensure(RenderTarget main) {
        if (depthPipeline == null) {
            depthPipeline = ScreenPass.pipeline("dof_depth", BindGroupLayout.builder()
                    .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DOFProjection", UniformType.UNIFORM_BUFFER).build(), GpuFormat.R32_FLOAT, null);
            prefilterPipeline = ScreenPass.pipeline("dof_prefilter", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DOFSettings", UniformType.UNIFORM_BUFFER).build(), HALF_FORMAT, null);
            blurPipeline = ScreenPass.pipeline("dof_blur", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DOFSettings", UniformType.UNIFORM_BUFFER).build(), HALF_FORMAT, null);
            compositePipeline = ScreenPass.pipeline("dof_composite", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("BlurSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DOFSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            projectionSettings = buffer("projection", 32);
            settings = buffer("settings", 48);
        }
        int halfWidth = Math.max(1, main.width / 2), halfHeight = Math.max(1, main.height / 2);
        if (distance == null) {
            distance = new TextureTarget("vector3_dof_distance", main.width, main.height, GpuFormat.R32_FLOAT, null);
            prefiltered = new TextureTarget("vector3_dof_prefiltered", halfWidth, halfHeight, HALF_FORMAT, null);
            blurred = new TextureTarget("vector3_dof_blurred", halfWidth, halfHeight, HALF_FORMAT, null);
        } else if (distance.width != main.width || distance.height != main.height) {
            distance.resize(main.width, main.height);
            prefiltered.resize(halfWidth, halfHeight);
            blurred.resize(halfWidth, halfHeight);
        }
    }

    private static GpuBuffer buffer(String name, int size) {
        return RenderSystem.getDevice().createBuffer(() -> "vector3_dof_" + name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }
}
