package ml.mypals.vectorthree.mc.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.GodRaysSettings;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import ml.mypals.vectorthree.core.fade.effects.ColorGradingSettings.Rgb;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/**
 * Light shafts, in four steps: the frame is taken whole, a mask pass keeps only what emits light (the sky, and
 * optionally very bright pixels) and blacks out the rest, that mask is radially blurred towards the sun, and the
 * result is added onto the original frame.
 */
public final class GodRaysEffect {
    private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final float MOON_SKY = 0.6f;
    private static final Rgb MOON_TINT = new Rgb(0.72f, 0.82f, 1);
    private static RenderPipeline maskPipeline, blurPipeline, compositePipeline;
    private static GpuBuffer maskSettings, blurSettings, compositeSettings;
    private static RenderTarget mask, rays;

    private GodRaysEffect() {}

    public static void render(RenderTarget main, GodRaysSettings value) {
        if (value.intensity() <= 0.001f || !value.sky() && !value.bright()) return;
        SunScreen.Light light = SunScreen.locate();
        if (light == null) return;
        ensure(main);
        boolean moon = light.kind() == SunScreen.Kind.MOON;
        // The night sky is too dark to send out rays by its own brightness, so the moon's sky has a floor.
        write(maskSettings, value.sky() ? 1 : 0, value.bright() ? 1 : 0, value.threshold(), moon ? MOON_SKY : 0);
        write(blurSettings, value.length(), value.decay(), value.samples(), value.falloff(),
                light.x(), light.y(), (float) main.width / main.height, 0);
        Rgb tint = moon ? MOON_TINT : value.tint();
        float gain = value.intensity() * light.fade();
        write(compositeSettings, tint.r() * gain, tint.g() * gain, tint.b() * gain, 0);

        try (RenderPass pass = begin("mask", mask.getColorTextureView())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(maskPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
            pass.setUniform("GodRaysMask", maskSettings);
            pass.draw(3, 1, 0, 0);
        }
        try (RenderPass pass = begin("blur", rays.getColorTextureView())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(blurPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", mask.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("GodRaysBlur", blurSettings);
            pass.draw(3, 1, 0, 0);
        }
        try (RenderPass pass = begin("composite", main.getColorTextureView())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", rays.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("GodRaysComposite", compositeSettings);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static RenderPass begin(String name, com.mojang.renderpearl.api.textures.GpuTextureView target) {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_god_rays_" + name, target, Optional.empty());
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
        if (maskPipeline == null) {
            maskPipeline = ScreenPass.pipeline("god_rays_mask", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("GodRaysMask", UniformType.UNIFORM_BUFFER).build(), FORMAT, null);
            blurPipeline = ScreenPass.pipeline("god_rays_blur", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("GodRaysBlur", UniformType.UNIFORM_BUFFER).build(), FORMAT, null);
            compositePipeline = ScreenPass.pipeline("god_rays_composite", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("GodRaysComposite", UniformType.UNIFORM_BUFFER).build(),
                    GpuFormat.RGBA8_UNORM, BlendFunction.ADDITIVE);
            maskSettings = buffer("mask", 16);
            blurSettings = buffer("blur", 32);
            compositeSettings = buffer("composite", 16);
        }
        int width = Math.max(1, main.width / 2), height = Math.max(1, main.height / 2);
        if (mask == null) {
            mask = new TextureTarget("vector3_god_rays_mask", width, height, FORMAT, null);
            rays = new TextureTarget("vector3_god_rays", width, height, FORMAT, null);
        } else if (mask.width != width || mask.height != height) {
            mask.resize(width, height);
            rays.resize(width, height);
        }
    }

    private static GpuBuffer buffer(String name, int size) {
        return RenderSystem.getDevice().createBuffer(() -> "vector3_god_rays_" + name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }
}
