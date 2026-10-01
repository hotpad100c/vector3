package ml.mypals.vectorthree.mc.vfx.effects;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import ml.mypals.vectorthree.core.fade.ScreenVFX;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class LutEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer settings;

    private LutEffect() {}

    public static void render(RenderTarget main, ScreenVFX value) {
        if (value.lutStrength() <= 0.001f) return;
        var lutView = EffectTextures.view(value.lut());
        if (lutView == null) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("lut", BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withSampler("LutSampler")
                    .withUniform("LutSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            settings = RenderSystem.getDevice().createBuffer(() -> "vector3_lut_settings",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 16).putVec4(value.lutStrength(), 0, 0, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_lut", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.bindTexture("LutSampler", lutView, ScreenPass.linear());
            pass.setUniform("LutSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
