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

public final class ColorCurvesEffect {
    private static final int SAMPLES = 64;
    private static RenderPipeline pipeline;
    private static GpuBuffer parameters;

    private ColorCurvesEffect() {}

    public static void render(RenderTarget main, GradingCurves value) {
        if (value.equals(GradingCurves.defaults())) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("color_curves", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("GradingCurves", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            parameters = RenderSystem.getDevice().createBuffer(() -> "vector3_color_curves",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SAMPLES * 32);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder data = Std140Builder.onStack(stack, SAMPLES * 32);
            for (int base = 0; base < 8; base += 4) {
                for (int i = 0; i < SAMPLES; i++) {
                    float x = i / (float) (SAMPLES - 1);
                    data.putVec4(value.get(base).evaluate(x), value.get(base + 1).evaluate(x),
                            value.get(base + 2).evaluate(x), value.get(base + 3).evaluate(x));
                }
            }
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), data.get());
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_color_curves", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("GradingCurves", parameters);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
