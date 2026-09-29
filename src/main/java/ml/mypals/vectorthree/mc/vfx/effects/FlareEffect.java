package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.FlareSettings;

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

public final class FlareEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer settings;
    private FlareEffect() {}

    public static void render(RenderTarget main, FlareSettings value) {
        if (value.intensity() <= 0.001f) return;
        SunScreen.Light light = SunScreen.locate();
        if (light == null || light.x() < 0 || light.x() > 1 || light.y() < 0 || light.y() > 1) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("flare", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("FlareSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            settings = RenderSystem.getDevice().createBuffer(() -> "vector3_flare_settings",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 48)
                    .putVec4(value.intensity(), value.threshold(), value.ghosts(), value.halo())
                    .putVec4(value.chromatic(), 1f / main.width, 1f / main.height,
                            (float) main.width / main.height)
                    .putVec4(light.x(), light.y(), light.fade(), light.kind() == SunScreen.Kind.MOON ? 1 : 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_flare", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
            pass.setUniform("FlareSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
