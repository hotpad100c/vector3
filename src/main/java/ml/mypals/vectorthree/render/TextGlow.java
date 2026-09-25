package ml.mypals.vectorthree.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Bloom for text: glowing text is drawn a second time into an HDR buffer, which is blurred at half and
 * quarter resolution and added onto the main target once per frame.
 */
public final class TextGlow {
    public static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final float SPREAD = 1.5f;

    private static RenderTarget glow;
    private static RenderTarget halfA;
    private static RenderTarget halfB;
    private static RenderTarget quarterA;
    private static RenderTarget quarterB;
    private static RenderPipeline blurPipeline;
    private static RenderPipeline compositePipeline;
    private static final GpuBuffer[] steps = new GpuBuffer[4];
    private static boolean preparedThisFrame;
    private static boolean usedThisFrame;
    private static int routeDepth;

    private TextGlow() {}

    public static boolean isRouting() {
        return routeDepth > 0;
    }

    /** Runs {@code draw} with Helpers#renderFeatures pointed at the glow buffer (see HelpersIrisBypassMixin). */
    public static void render(Runnable draw) {
        IrisBypassTarget.beginIrisBypass();
        routeDepth++;
        try {
            draw.run();
        } finally {
            routeDepth--;
            IrisBypassTarget.endIrisBypass();
        }
    }

    public static RenderTarget prepareForDraw() {
        ensureTargets();
        if (!preparedThisFrame) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(glow.getColorTexture(), new Vector4f(0, 0, 0, 0));
            glow.copyDepthFrom(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            preparedThisFrame = true;
        }
        usedThisFrame = true;
        return glow;
    }

    public static void composite() {
        boolean used = usedThisFrame;
        preparedThisFrame = false;
        usedThisFrame = false;
        if (!used || glow == null) return;
        ensurePipelines();
        blur(glow, halfA, 0, 1, 0);
        blur(halfA, halfB, 1, 0, 1);
        blur(halfB, quarterA, 2, 1, 0);
        blur(quarterA, quarterB, 3, 0, 1);

        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_text_glow_composite", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HalfSampler", halfB.getColorTextureView(), linear);
            pass.setUniform("QuarterSampler", quarterB.getColorTextureView(), linear);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void blur(RenderTarget source, RenderTarget target, int index, float dx, float dy) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, 16)
                    .putVec4(dx * SPREAD / target.width, dy * SPREAD / target.height, 0, 0).get();
            encoder.writeToBuffer(steps[index].slice(), data);
        }
        try (RenderPass pass = encoder.createRenderPass(() -> "vector3_text_glow_blur", target.getColorTextureView(),
                Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(blurPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("GlowBlur", steps[index]);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void ensurePipelines() {
        if (blurPipeline != null) return;
        blurPipeline = RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Vector3.id("pipeline/text_glow_blur"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Vector3.id("post/glow_blur"))
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("GlowBlur", UniformType.UNIFORM_BUFFER)
                        .build())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.empty(), FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(Optional.empty())
                .build());
        compositePipeline = RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Vector3.id("pipeline/text_glow_composite"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Vector3.id("post/glow_composite"))
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("HalfSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .withUniform("QuarterSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                        .build())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA8_UNORM,
                        ColorTargetState.WRITE_COLOR))
                .withDepthStencilState(Optional.empty())
                .build());
        for (int i = 0; i < steps.length; i++) {
            int index = i;
            steps[i] = RenderSystem.getDevice().createBuffer(() -> "vector3_text_glow_step_" + index,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        }
    }

    private static void ensureTargets() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = Math.max(1, main.width), height = Math.max(1, main.height);
        if (glow == null) {
            glow = new TextureTarget("vector3_text_glow", width, height, FORMAT, GpuFormat.D32_FLOAT);
            halfA = new TextureTarget("vector3_text_glow_half_a", half(width), half(height), FORMAT, null);
            halfB = new TextureTarget("vector3_text_glow_half_b", half(width), half(height), FORMAT, null);
            quarterA = new TextureTarget("vector3_text_glow_quarter_a", quarter(width), quarter(height), FORMAT, null);
            quarterB = new TextureTarget("vector3_text_glow_quarter_b", quarter(width), quarter(height), FORMAT, null);
        } else if (glow.width != width || glow.height != height) {
            glow.resize(width, height);
            halfA.resize(half(width), half(height));
            halfB.resize(half(width), half(height));
            quarterA.resize(quarter(width), quarter(height));
            quarterB.resize(quarter(width), quarter(height));
        }
    }

    private static int half(int size) {
        return Math.max(1, size / 2);
    }

    private static int quarter(int size) {
        return Math.max(1, size / 4);
    }
}
