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
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class GrainEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer parameters;
    private GrainEffect() {}

    public static void render(RenderTarget main, GrainSettings value) {
        if (value.intensity() <= 0.001f) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("grain", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("GrainSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            parameters = RenderSystem.getDevice().createBuffer(() -> "vector3_grain_settings",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
        }
        float time = Minecraft.getInstance().level == null ? 0
                : Minecraft.getInstance().level.getGameTime() * value.speed();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 32)
                    .putVec4(value.intensity(), value.size(), time, value.colored() ? 1 : 0)
                    .putVec4(main.width, main.height, 0, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_grain", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("GrainSettings", parameters);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
