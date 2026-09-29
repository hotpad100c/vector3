package ml.mypals.vectorthree.mc.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.ReflectionSettings;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class ReflectionEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer settings;
    private ReflectionEffect() {}

    public static void render(RenderTarget main, ReflectionSettings value) {
        if (!DepthOfFieldEffect.hasCapturedDepth() || value.intensity() <= 0.001f) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("reflection", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("ReflectionSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            settings = RenderSystem.getDevice().createBuffer(() -> "vector3_reflection",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 32)
                    .putVec4(value.intensity(), value.maxDistance(), value.thickness(), value.steps())
                    .putVec4(1f / main.width, 1f / main.height, DepthOfFieldEffect.projectionX(),
                            DepthOfFieldEffect.projectionY()).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        assert target.getColorTextureView() != null;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_reflection", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
            pass.setUniform("ReflectionSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
