package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.BlurSettings;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/**
 * Gaussian blur. The frame is halved until the kernel is small (a Gaussian is separable, so two passes of at most
 * 24 taps each side), blurred there, and the result is scaled back up and blended over the original.
 */
public final class BlurEffect {
    private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int MAX_LEVELS = 4;
    private static final float MAX_SIGMA = 5;
    private static final int MAX_TAPS = 24;
    private static RenderPipeline downPipeline, passPipeline, compositePipeline;
    private static GpuBuffer horizontal, vertical, composite;
    // chain[k] is 1/2^(k+1) of the frame; ping[n] and pong[n] are the working pair at 1/2^n.
    private static final RenderTarget[] chain = new RenderTarget[MAX_LEVELS - 1];
    private static final RenderTarget[] ping = new RenderTarget[MAX_LEVELS + 1];
    private static final RenderTarget[] pong = new RenderTarget[MAX_LEVELS + 1];

    private BlurEffect() {}

    public static void render(RenderTarget main, BlurSettings value) {
        if (value.amount() <= 0.001f || value.radius() <= 0.05f) return;
        // The radius is in pixels of a 1080p frame; a Gaussian reaches about three sigma.
        float sigmaFull = Math.max(0.5f, value.radius() * main.height / 1080f * 0.5f);
        int levels = 0;
        float sigma = sigmaFull;
        while (sigma > MAX_SIGMA && levels < MAX_LEVELS) sigma = sigmaFull / (1 << ++levels);
        int taps = Math.min(MAX_TAPS, (int) Math.ceil(sigma * 3));
        ensure(main, levels);

        RenderTarget source = main;
        for (int k = 0; k < levels; k++) {
            RenderTarget target = k == levels - 1 ? ping[levels] : chain[k];
            pass("down", source, target, downPipeline, null);
            source = target;
        }
        RenderTarget first = levels == 0 ? main : ping[levels];
        RenderTarget second = levels == 0 ? ping[0] : pong[levels];
        RenderTarget result = levels == 0 ? pong[0] : ping[levels];
        write(horizontal, 1f / first.width, 0, sigma, taps);
        write(vertical, 0, 1f / second.height, sigma, taps);
        pass("h", first, second, passPipeline, horizontal);
        pass("v", second, result, passPipeline, vertical);

        write(composite, value.amount(), value.clear(), value.feather(), (float) main.width / main.height);
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_blur_composite", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("BlurSampler", result.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("BlurComposite", composite);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }

    private static void pass(String name, RenderTarget from, RenderTarget to, RenderPipeline pipeline,
                             GpuBuffer settings) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_blur_" + name, to.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", from.getColorTextureView(), ScreenPass.linear());
            if (settings != null) pass.setUniform("BlurPass", settings);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void write(GpuBuffer buffer, float x, float y, float z, float w) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 16).putVec4(x, y, z, w).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    private static void ensure(RenderTarget main, int levels) {
        if (downPipeline == null) {
            downPipeline = ScreenPass.pipeline("blur_down", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER).build(), FORMAT, null);
            passPipeline = ScreenPass.pipeline("blur_pass", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("BlurPass", UniformType.UNIFORM_BUFFER).build(), FORMAT, null);
            compositePipeline = ScreenPass.pipeline("blur_composite", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("BlurSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("BlurComposite", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            horizontal = buffer("horizontal");
            vertical = buffer("vertical");
            composite = buffer("composite");
        }
        // Only the targets this level uses; the others stay unallocated until a radius needs them.
        for (int k = 0; k < levels - 1; k++) chain[k] = fit(chain[k], "chain_" + k, main, k + 1);
        ping[levels] = fit(ping[levels], "ping_" + levels, main, levels);
        pong[levels] = fit(pong[levels], "pong_" + levels, main, levels);
    }

    private static RenderTarget fit(RenderTarget target, String name, RenderTarget main, int shift) {
        int width = Math.max(1, main.width >> shift), height = Math.max(1, main.height >> shift);
        if (target == null) return new TextureTarget("vector3_blur_" + name, width, height, FORMAT, null);
        if (target.width != width || target.height != height) target.resize(width, height);
        return target;
    }

    private static GpuBuffer buffer(String name) {
        return RenderSystem.getDevice().createBuffer(() -> "vector3_blur_" + name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
    }
}
