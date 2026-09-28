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

import java.nio.ByteBuffer;
import java.util.Optional;

import static ml.mypals.vectorthree.flashback.fade.effects.ColorGradingSettings.Rgb;

public final class ColorGradingEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer parameters;

    private ColorGradingEffect() {}

    public static void render(RenderTarget main, ColorGradingSettings value) {
        ensurePipeline();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, 128)
                    .putVec4(value.exposure(), value.temperature(), value.tint(), value.hue())
                    .putVec4(value.saturation(), value.contrast(), value.toneMap(), 0);
            putRgb(builder, value.mixerRed());
            putRgb(builder, value.mixerGreen());
            putRgb(builder, value.mixerBlue());
            putRgb(builder, value.lift());
            putRgb(builder, value.gamma());
            putRgb(builder, value.gain());
            ByteBuffer data = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_color_grading", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("ColorGradingSettings", parameters);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
        ColorCurvesEffect.render(main, value.curves());
        AdvancedGradingEffect.render(main, value.advanced());
    }

    private static void putRgb(Std140Builder builder, Rgb value) {
        builder.putVec4(value.r(), value.g(), value.b(), 0);
    }

    private static void ensurePipeline() {
        if (pipeline != null) return;
        pipeline = ScreenPass.pipeline("color_grading", BindGroupLayout.builder()
                .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("ColorGradingSettings", UniformType.UNIFORM_BUFFER).build(),
                GpuFormat.RGBA8_UNORM, null);
        parameters = RenderSystem.getDevice().createBuffer(() -> "vector3_color_grading_settings",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 128);
    }
}
