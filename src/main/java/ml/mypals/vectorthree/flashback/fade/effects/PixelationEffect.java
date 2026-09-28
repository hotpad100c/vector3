package ml.mypals.vectorthree.flashback.fade.effects;

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

public final class PixelationEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer parameters;
    private PixelationEffect() {}

    public static void render(RenderTarget main, PixelationSettings value) {
        if (value.blockSize() <= 1.001f && value.colorLevels() >= 256) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("pixelation", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("PixelationSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            parameters = RenderSystem.getDevice().createBuffer(() -> "vector3_pixelation_settings",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 16).putVec4(value.blockSize(), value.colorLevels(),
                    main.width, main.height).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        assert target.getColorTextureView() != null;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_pixelation", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("PixelationSettings", parameters);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
