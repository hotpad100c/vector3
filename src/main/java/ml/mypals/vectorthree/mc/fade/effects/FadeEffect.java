package ml.mypals.vectorthree.mc.fade.effects;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import ml.mypals.vectorthree.core.fade.ScreenVFX;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class FadeEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer colour;

    private FadeEffect() {}

    public static void render(RenderTarget main, ScreenVFX value) {
        if (value.opacity() <= 0.001f) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("fade", BindGroupLayout.builder()
                    .withUniform("FadeColor", UniformType.UNIFORM_BUFFER).build(),
                    GpuFormat.RGBA8_UNORM, BlendFunction.TRANSLUCENT);
            colour = RenderSystem.getDevice().createBuffer(() -> "vector3_fade_colour",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 16).putVec4(value.red(), value.green(),
                    value.blue(), Math.clamp(value.opacity(), 0, 1)).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(colour.slice(), data);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_fade", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FadeColor", colour);
            pass.draw(3, 1, 0, 0);
        }
    }
}
