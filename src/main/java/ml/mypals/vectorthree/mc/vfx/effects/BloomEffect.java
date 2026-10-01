package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.BloomSettings;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import ml.mypals.vectorthree.core.fade.effects.ColorGradingSettings.Rgb;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/** Five-level HDR blur pyramid, composited onto the LDR scene. */
public final class BloomEffect {
    private static final int LEVELS = 5;
    private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final RenderTarget[] down = new RenderTarget[LEVELS], up = new RenderTarget[LEVELS];
    private static final GpuBuffer[] steps = new GpuBuffer[LEVELS * 2];
    private static RenderPipeline prefilterPipeline, downPipeline, upPipeline, compositePipeline;
    private static GpuBuffer prefilterSettings, compositeSettings;

    private BloomEffect() {}

    public static void render(RenderTarget main, BloomSettings value) {
        if (value.intensity() <= 0.001f) return;
        ensure(main);
        write(prefilterSettings, value.threshold(), value.softKnee(), value.clamp(), value.antiFlicker() ? 1 : 0,
                1f / main.width, 1f / main.height, value.highQuality() ? 1 : 0, 0);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_bloom_prefilter", down[0].getColorTextureView(), Optional.empty())) {
            pass.setPipeline(prefilterPipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("BloomPrefilter", prefilterSettings);
            pass.draw(3, 1, 0, 0);
        }
        for (int level = 1; level < LEVELS; level++) {
            RenderTarget source = down[level - 1];
            write(steps[level], 1f / source.width, 1f / source.height, value.highQuality() ? 1 : 0, 0);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "vector3_bloom_down", down[level].getColorTextureView(), Optional.empty())) {
                pass.setPipeline(downPipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture("InSampler", source.getColorTextureView(), ScreenPass.linear());
                pass.setUniform("BloomStep", steps[level]);
                pass.draw(3, 1, 0, 0);
            }
        }
        RenderTarget wide = down[LEVELS - 1];
        for (int level = LEVELS - 2; level >= 0; level--) {
            float scatter = Math.clamp(value.radius() * 1.2f, 0.01f, 1f);
            write(steps[LEVELS + level], 1f / wide.width, 1f / wide.height, scatter, 0);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "vector3_bloom_up", up[level].getColorTextureView(), Optional.empty())) {
                pass.setPipeline(upPipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture("PrevSampler", wide.getColorTextureView(), ScreenPass.linear());
                pass.bindTexture("CurrentSampler", down[level].getColorTextureView(), ScreenPass.linear());
                pass.setUniform("BloomStep", steps[LEVELS + level]);
                pass.draw(3, 1, 0, 0);
            }
            wide = up[level];
        }
        var dirtView = value.dirtIntensity() > 0 ? EffectTextures.view(value.dirtTexture()) : null;
        boolean hasDirt = dirtView != null;
        if (!hasDirt) dirtView = wide.getColorTextureView();
        Rgb tint = value.tint();
        write(compositeSettings, tint.r() * value.intensity(), tint.g() * value.intensity(),
                tint.b() * value.intensity(), hasDirt ? value.dirtIntensity() : 0);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_bloom_composite", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(compositePipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", wide.getColorTextureView(), ScreenPass.linear());
            pass.bindTexture("DirtSampler", dirtView, ScreenPass.linear());
            pass.setUniform("BloomComposite", compositeSettings);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void write(GpuBuffer buffer, float x, float y, float z, float w) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 16).putVec4(x, y, z, w).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    private static void write(GpuBuffer buffer, float a, float b, float c, float d,
                              float e, float f, float g, float h) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 32).putVec4(a, b, c, d).putVec4(e, f, g, h).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    private static void ensure(RenderTarget main) {
        if (prefilterPipeline == null) {
            prefilterPipeline = ScreenPass.pipeline("bloom_prefilter", BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withUniform("BloomPrefilter", UniformType.UNIFORM_BUFFER).build(), FORMAT, null);
            downPipeline = ScreenPass.pipeline("bloom_down", BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withUniform("BloomStep", UniformType.UNIFORM_BUFFER).build(), FORMAT, null);
            upPipeline = ScreenPass.pipeline("bloom_up", BindGroupLayout.builder()
                    .withSampler("PrevSampler")
                    .withSampler("CurrentSampler")
                    .withUniform("BloomStep", UniformType.UNIFORM_BUFFER).build(), FORMAT, null);
            compositePipeline = ScreenPass.pipeline("bloom_composite", BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withSampler("DirtSampler")
                    .withUniform("BloomComposite", UniformType.UNIFORM_BUFFER).build(),
                    GpuFormat.RGBA8_UNORM, BlendFunction.ADDITIVE);
            prefilterSettings = buffer("prefilter", 32);
            compositeSettings = buffer("composite", 16);
            for (int i = 0; i < steps.length; i++) steps[i] = buffer("step_" + i, 16);
        }
        for (int i = 0; i < LEVELS; i++) {
            int w = Math.max(1, main.width >> (i + 1)), h = Math.max(1, main.height >> (i + 1));
            if (down[i] == null) {
                down[i] = new TextureTarget("vector3_bloom_down_" + i, w, h, false, FORMAT);
                up[i] = new TextureTarget("vector3_bloom_up_" + i, w, h, false, FORMAT);
            } else if (down[i].width != w || down[i].height != h) {
                down[i].resize(w, h);
                up[i].resize(w, h);
            }
        }
    }

    private static GpuBuffer buffer(String name, int size) {
        return RenderSystem.getDevice().createBuffer(() -> "vector3_bloom_" + name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }
}
