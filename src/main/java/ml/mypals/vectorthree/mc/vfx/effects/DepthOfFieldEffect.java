package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.DofSettings;

import ml.mypals.vectorthree.mc.camera.PreviewPass;
import ml.mypals.vectorthree.core.Mod;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import ml.mypals.vectorthree.core.port.Ports;
import ml.mypals.vectorthree.core.fade.ScreenVFX;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4fc;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

/**
 * Depth is captured into a linear view-distance texture before the hand pass clears it. The frame is then copied
 * into a mip chain with its CoC in alpha, and each bokeh tap reads the mip level matching the tap spacing, so a
 * few dozen taps give a smooth blur. Foreground blur is dilated at half resolution so it can spill over sharp edges.
 */
public final class DepthOfFieldEffect {
    public static final float MAX_STRENGTH = 2;
    private static final GpuFormat MIP_FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int MAX_LEVELS = 7;
    private static final Autofocus[] AUTOFOCUS = {new Autofocus(), new Autofocus()};
    private static RenderPipeline depthPipeline, mip0Pipeline, mipPipeline, dilatePipeline, blurPipeline,
            autofocusPipeline;
    private static RenderTarget distance, dilateH, dilateV;
    private static GpuTexture mips;
    private static GpuTextureView mipsView;
    private static GpuTextureView[] levels = new GpuTextureView[0];
    private static GpuBuffer projectionSettings, settings, horizontal, vertical, autofocusSettings;
    private static boolean depthReady;
    private static float focalScale = 1;
    private static float projectionX = 1, projectionY = 1;
    private static final Matrix4f projectionMatrix = new Matrix4f(), inverseProjectionMatrix = new Matrix4f();
    private static int frame;

    /** Smoothed centre distance, one per view so the camera preview does not disturb the main view. */
    private static final class Autofocus {
        RenderTarget current, previous;
        long lastNanos;
        int lastFrame = -1;
        boolean valid, exporting;
    }

    private DepthOfFieldEffect() {}

    public static void captureDepth(RenderTarget main, Matrix4fc projection) {
        if (main.getDepthTextureView() == null || main.width <= 0 || main.height <= 0) return;
        ensure(main);
        focalScale = Math.abs(projection.m11());
        projectionX = projection.m00();
        projectionY = projection.m11();
        projectionMatrix.set(projection);
        inverseProjectionMatrix.set(projection).invert();
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        write(projectionSettings, projection.m22(), projection.m32(), projection.m23(), projection.m33(),
                zeroToOne ? 1 : 0, skyDepth(projection, zeroToOne), 0, 0);
        try (RenderPass pass = begin("depth", distance.getColorTextureView(), depthPipeline)) {
            pass.setUniform("DepthSampler", main.getDepthTextureView(), ScreenPass.nearest());
            pass.setUniform("DOFProjection", projectionSettings);
            pass.draw(3, 1, 0, 0);
        }
        depthReady = true;
    }

    /** The depth-buffer value of the far plane, which the sky keeps: 0 with MC's reverse-Z, 1 otherwise. */
    private static float skyDepth(Matrix4fc projection, boolean zeroToOne) {
        return distanceAt(projection, zeroToOne, 0) > distanceAt(projection, zeroToOne, 1) ? 0 : 1;
    }

    private static double distanceAt(Matrix4fc projection, boolean zeroToOne, float depth) {
        double ndc = zeroToOne ? depth : depth * 2 - 1;
        double distance = (ndc * projection.m33() - projection.m32()) / (ndc * projection.m23() - projection.m22());
        return Double.isFinite(distance) && distance > 0 ? distance : Double.POSITIVE_INFINITY;
    }

    public static void endFrame() {
        depthReady = false;
        frame++;
    }

    public static boolean hasCapturedDepth() { return depthReady; }
    public static GpuTextureView distanceView() { return depthReady ? distance.getColorTextureView() : null; }
    public static float projectionX() { return projectionX; }
    public static float projectionY() { return projectionY; }
    public static Matrix4fc projectionMatrix() { return projectionMatrix; }
    public static Matrix4fc inverseProjectionMatrix() { return inverseProjectionMatrix; }

    public static void render(RenderTarget main, ScreenVFX value, boolean overlay) {
        if (!depthReady || distance.width != main.width || distance.height != main.height) return;
        float maxRadius = Math.clamp(value.dofStrength(), 0, MAX_STRENGTH) * main.height * 0.03f;
        boolean blur = maxRadius >= 0.5f;
        if (!blur && !overlay) return;
        DofSettings dof = value.dof();
        boolean distanceBlur = value.dofMode() == ScreenVFX.DOF_DISTANCE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 80)
                    .putVec4(value.focusDistance(), value.focusRange() * 0.5f, value.dofMode(), blur ? maxRadius : 0)
                    .putVec4(dof.aperture(), dof.fovScaled() ? focalScale * 0.8f : 1, dof.tiltX(), dof.tiltY())
                    .putVec4(dof.shape(), dof.samples(), dof.rings(), (float) Math.toRadians(dof.rotation()))
                    .putVec4(overlay && !distanceBlur ? 1 : 0, dof.chromatic() ? 1 : 0, dof.anamorphic() ? 1 : 0,
                            dof.autofocus() && !distanceBlur ? 1 : 0)
                    .putVec4(1f / main.width, 1f / main.height, (float) main.height / main.width,
                            dof.chromaticStrength()).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        GpuTextureView focus = dof.autofocus() && !distanceBlur ? autofocus(dof) : distance.getColorTextureView();
        try (RenderPass pass = begin("mip0", levels[0], mip0Pipeline)) {
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("DistanceSampler", distance.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("FocusSampler", focus, ScreenPass.nearest());
            pass.setUniform("DOFSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        if (blur) {
            for (int level = 1; level < levels.length; level++) {
                try (RenderPass pass = begin("mip", levels[level], mipPipeline)) {
                    pass.setUniform("InSampler", levels[level - 1], ScreenPass.linear());
                    pass.draw(3, 1, 0, 0);
                }
            }
            write(horizontal, 1f / mips.getWidth(1), 0, 1, 0, 0, 0, 0, 0);
            write(vertical, 0, 1f / mips.getHeight(1), 0, 0, 0, 0, 0, 0);
            dilate(levels[1], dilateH, horizontal);
            dilate(dilateH.getColorTextureView(), dilateV, vertical);
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = begin("blur", target.getColorTextureView(), blurPipeline)) {
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("MipSampler", mipsView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true));
            pass.setUniform("NearSampler", dilateV.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("DistanceSampler", distance.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("FocusSampler", focus, ScreenPass.nearest());
            pass.setUniform("DOFSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }

    /** Eases the focus towards the centre distance, in log space, with the given half-life. */
    private static GpuTextureView autofocus(DofSettings dof) {
        Autofocus state = AUTOFOCUS[PreviewPass.isRendering() ? 1 : 0];
        if (state.current == null) {
            state.current = new TextureTarget("vector3_dof_focus_a", 1, 1, GpuFormat.R32_FLOAT, null);
            state.previous = new TextureTarget("vector3_dof_focus_b", 1, 1, GpuFormat.R32_FLOAT, null);
        }
        if (state.lastFrame == frame) return state.current.getColorTextureView();
        boolean exporting = Ports.clock().exporting();
        if (exporting != state.exporting) state.valid = false;
        long now = System.nanoTime();
        double seconds;
        if (exporting && Ports.clock().exportFrameSeconds() > 0) seconds = Ports.clock().exportFrameSeconds();
        else seconds = Math.clamp((now - state.lastNanos) / 1e9, 0, 0.25);
        float blend = state.valid ? (float) (1 - Math.pow(0.5, seconds / dof.autofocusSmoothing())) : 1;
        state.lastNanos = now;
        state.lastFrame = frame;
        state.exporting = exporting;
        state.valid = true;

        RenderTarget previous = state.current;
        state.current = state.previous;
        state.previous = previous;
        write(autofocusSettings, blend, 0, 0, 0, 0, 0, 0, 0);
        try (RenderPass pass = begin("autofocus", state.current.getColorTextureView(), autofocusPipeline)) {
            pass.setUniform("DistanceSampler", distance.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("PrevSampler", previous.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("AutofocusSettings", autofocusSettings);
            pass.draw(3, 1, 0, 0);
        }
        return state.current.getColorTextureView();
    }

    private static void dilate(GpuTextureView source, RenderTarget target, GpuBuffer direction) {
        try (RenderPass pass = begin("dilate", target.getColorTextureView(), dilatePipeline)) {
            pass.setUniform("InSampler", source, ScreenPass.nearest());
            pass.setUniform("DOFSettings", settings);
            pass.setUniform("DOFDirection", direction);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static RenderPass begin(String name, GpuTextureView target, RenderPipeline pipeline) {
        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_dof_" + name, target, Optional.empty());
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(pass);
        return pass;
    }

    private static void write(GpuBuffer buffer, float a, float b, float c, float d,
                              float e, float f, float g, float h) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 32).putVec4(a, b, c, d).putVec4(e, f, g, h).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    private static void ensure(RenderTarget main) {
        if (depthPipeline == null) {
            depthPipeline = pipeline("dof_depth", GpuFormat.R32_FLOAT, "DepthSampler", "DOFProjection");
            mip0Pipeline = pipeline("dof_mip0", MIP_FORMAT, "InSampler", "DistanceSampler", "FocusSampler",
                    "DOFSettings");
            autofocusPipeline = pipeline("dof_autofocus", GpuFormat.R32_FLOAT,
                    "DistanceSampler", "PrevSampler", "AutofocusSettings");
            mipPipeline = pipeline("dof_mip", MIP_FORMAT, "InSampler");
            dilatePipeline = pipeline("dof_dilate", GpuFormat.R16_FLOAT, "InSampler", "DOFSettings", "DOFDirection");
            blurPipeline = pipeline("dof_blur", GpuFormat.RGBA8_UNORM,
                    "InSampler", "MipSampler", "NearSampler", "DistanceSampler", "FocusSampler", "DOFSettings");
            projectionSettings = buffer("projection", 32);
            settings = buffer("settings", 80);
            horizontal = buffer("horizontal", 32);
            vertical = buffer("vertical", 32);
            autofocusSettings = buffer("autofocus", 32);
        }
        int halfWidth = Math.max(1, main.width >> 1), halfHeight = Math.max(1, main.height >> 1);
        if (distance == null) {
            distance = new TextureTarget("vector3_dof_distance", main.width, main.height, GpuFormat.R32_FLOAT, null);
            dilateH = new TextureTarget("vector3_dof_dilate_h", halfWidth, halfHeight, GpuFormat.R16_FLOAT, null);
            dilateV = new TextureTarget("vector3_dof_dilate_v", halfWidth, halfHeight, GpuFormat.R16_FLOAT, null);
        } else if (distance.width != main.width || distance.height != main.height) {
            distance.resize(main.width, main.height);
            dilateH.resize(halfWidth, halfHeight);
            dilateV.resize(halfWidth, halfHeight);
        }
        if (mips == null || mips.getWidth(0) != main.width || mips.getHeight(0) != main.height) createMips(main);
    }

    private static void createMips(RenderTarget main) {
        for (GpuTextureView level : levels) level.close();
        if (mipsView != null) mipsView.close();
        if (mips != null) mips.close();
        int count = Math.max(2, Math.min(MAX_LEVELS,
                32 - Integer.numberOfLeadingZeros(Math.max(1, Math.min(main.width, main.height)))));
        mips = RenderSystem.getDevice().createTexture(() -> "vector3_dof_mips",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, MIP_FORMAT,
                main.width, main.height, 1, count);
        mipsView = RenderSystem.getDevice().createTextureView(mips);
        levels = new GpuTextureView[count];
        for (int i = 0; i < count; i++) levels[i] = RenderSystem.getDevice().createTextureView(mips, i, 1);
    }

    private static RenderPipeline pipeline(String name, GpuFormat format, String... uniforms) {
        BindGroupLayout.Builder layout = BindGroupLayout.builder();
        for (String uniform : uniforms)
            layout = layout.withUniform(uniform, uniform.endsWith("Sampler")
                    ? UniformType.COMBINED_IMAGE_SAMPLER : UniformType.UNIFORM_BUFFER);
        // The mip chain keeps its CoC in alpha, and ScreenPass pipelines only write RGB.
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Mod.id("pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Mod.id("post/" + name))
                .withBindGroupLayout(layout.build())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(Optional.empty()).build());
    }

    private static GpuBuffer buffer(String name, int size) {
        return RenderSystem.getDevice().createBuffer(() -> "vector3_dof_" + name,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
    }
}
