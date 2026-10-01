package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.ExposureSettings;

import ml.mypals.vectorthree.mc.camera.PreviewPass;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import ml.mypals.vectorthree.core.port.Ports;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/** Geometric-mean metering with a ping-pong temporal exposure value. */
public final class AutoExposureEffect {
    private static RenderPipeline meterPipeline, adaptPipeline, applyPipeline;
    private static RenderTarget luminance;
    private static GpuBuffer settings;
    private static final History[] HISTORY = { new History(), new History() };

    private static final class History {
        RenderTarget exposureA, exposureB;
        long lastNanos;
        boolean valid, exporting;
    }

    private AutoExposureEffect() {}

    public static void clear() {
        for (History history : HISTORY) { history.valid = false; history.lastNanos = 0; }
    }

    public static void render(RenderTarget main, ExposureSettings value) {
        ensure();
        History history = HISTORY[PreviewPass.isRendering() ? 1 : 0];
        if (history.exposureA == null) {
            String suffix = PreviewPass.isRendering() ? "preview" : "main";
            history.exposureA = new TextureTarget("vector3_exposure_a_" + suffix, 1, 1, false, GpuFormat.R32_FLOAT);
            history.exposureB = new TextureTarget("vector3_exposure_b_" + suffix, 1, 1, false, GpuFormat.R32_FLOAT);
        }
        boolean nowExporting = Ports.clock().exporting();
        if (nowExporting != history.exporting) history.valid = false;
        long now = System.nanoTime();
        double seconds = nowExporting && Ports.clock().exportFrameSeconds() > 0
                ? Ports.clock().exportFrameSeconds()
                : history.lastNanos == 0 ? 0.05 : Math.clamp((now - history.lastNanos) / 1e9, 0, 0.25);
        history.lastNanos = now;
        history.exporting = nowExporting;
        float blend = history.valid ? (float) (1 - Math.exp(-seconds * value.speed())) : 1;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 32)
                    .putVec4(blend, value.minEv(), value.maxEv(), value.compensation())
                    .putVec4(value.target(), history.valid ? 1 : 0, 0, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        try (RenderPass pass = begin("meter", luminance, meterPipeline)) {
            pass.bindTexture("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.draw(3, 1, 0, 0);
        }
        RenderTarget previous = history.exposureA;
        history.exposureA = history.exposureB;
        history.exposureB = previous;
        try (RenderPass pass = begin("adapt", history.exposureA, adaptPipeline)) {
            pass.bindTexture("LumSampler", luminance.getColorTextureView(), ScreenPass.nearest());
            pass.bindTexture("PrevSampler", history.exposureB.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("ExposureSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        history.valid = true;
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = begin("apply", target, applyPipeline)) {
            pass.bindTexture("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.bindTexture("ExposureSampler", history.exposureA.getColorTextureView(), ScreenPass.nearest());
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }

    private static RenderPass begin(String name, RenderTarget target, RenderPipeline pipeline) {
        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_exposure_" + name, target.getColorTextureView(), Optional.empty());
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        return pass;
    }

    private static void ensure() {
        if (meterPipeline != null) return;
        meterPipeline = ScreenPass.pipeline("exposure_meter", BindGroupLayout.builder()
                .withSampler("InSampler").build(), GpuFormat.R32_FLOAT, null);
        adaptPipeline = ScreenPass.pipeline("exposure_adapt", BindGroupLayout.builder()
                .withSampler("LumSampler")
                .withSampler("PrevSampler")
                .withUniform("ExposureSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.R32_FLOAT, null);
        applyPipeline = ScreenPass.pipeline("exposure_apply", BindGroupLayout.builder()
                .withSampler("InSampler")
                .withSampler("ExposureSampler").build(), GpuFormat.RGBA8_UNORM, null);
        settings = RenderSystem.getDevice().createBuffer(() -> "vector3_auto_exposure",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
        luminance = new TextureTarget("vector3_exposure_luminance", 1, 1, false, GpuFormat.R32_FLOAT);
    }
}
