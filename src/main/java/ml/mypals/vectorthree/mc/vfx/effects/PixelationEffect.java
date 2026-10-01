package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.PixelationSettings;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
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
                    .withSampler("InSampler")
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
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", main.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("PixelationSettings", parameters);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
