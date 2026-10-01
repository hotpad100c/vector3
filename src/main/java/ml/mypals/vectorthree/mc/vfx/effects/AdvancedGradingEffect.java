package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.AdvancedGradingSettings;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import ml.mypals.vectorthree.core.fade.effects.ColorGradingSettings.Rgb;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class AdvancedGradingEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer parameters;
    private AdvancedGradingEffect() {}

    public static void render(RenderTarget main, AdvancedGradingSettings value) {
        if (value.equals(AdvancedGradingSettings.defaults())) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("advanced_grading", BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withUniform("AdvancedGradingSettings", UniformType.UNIFORM_BUFFER).build(),
                    GpuFormat.RGBA8_UNORM, null);
            parameters = RenderSystem.getDevice().createBuffer(() -> "vector3_advanced_grading",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 96);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder data = Std140Builder.onStack(stack, 96);
            put(data, value.splitShadows()); put(data, value.splitHighlights());
            put(data, value.shadows()); put(data, value.midtones()); put(data, value.highlights());
            data.putVec4(value.splitBalance(), value.shadowEnd(), value.highlightStart(), 0);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), data.get());
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_advanced_grading", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("AdvancedGradingSettings", parameters);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }

    private static void put(Std140Builder data, Rgb rgb) { data.putVec4(rgb.r(), rgb.g(), rgb.b(), 0); }
}
