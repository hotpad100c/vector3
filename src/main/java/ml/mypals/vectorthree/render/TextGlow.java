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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Bloom for text: glowing text is drawn a second time into an HDR buffer, which is blurred at half and
 * quarter resolution and added onto the main target once per frame. Each spread (in quarter steps) gets
 * its own buffers, since one blur can only have one width.
 */
public final class TextGlow {
    public static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int IDLE_FRAMES_BEFORE_FREE = 5;

    private static final class Layer {
        final float spread;
        RenderTarget glow, halfA, halfB, quarterA, quarterB;
        final GpuBuffer[] steps = new GpuBuffer[4];
        boolean prepared, used;
        int idleFrames;

        Layer(float spread) {
            this.spread = spread;
        }

        void close() {
            for (RenderTarget target : new RenderTarget[]{glow, halfA, halfB, quarterA, quarterB}) {
                if (target != null) target.destroyBuffers();
            }
            for (GpuBuffer step : steps) {
                if (step != null) step.close();
            }
        }
    }

    private static final Map<Integer, Layer> LAYERS = new HashMap<>();
    private static RenderPipeline blurPipeline;
    private static RenderPipeline compositePipeline;
    private static Layer routing;
    private static int routeDepth;

    private TextGlow() {}

    public static boolean isRouting() {
        return routeDepth > 0 && routing != null;
    }

    /** Runs {@code draw} with Helpers#renderFeatures pointed at this spread's glow buffer (see HelpersIrisBypassMixin). */
    public static void render(float spread, Runnable draw) {
        int key = Math.max(1, Math.round(spread * 4));
        Layer previous = routing;
        routing = LAYERS.computeIfAbsent(key, k -> new Layer(k / 4f));
        IrisBypassTarget.beginIrisBypass();
        routeDepth++;
        try {
            draw.run();
        } finally {
            routeDepth--;
            IrisBypassTarget.endIrisBypass();
            routing = previous;
        }
    }

    public static RenderTarget prepareForDraw() {
        Layer layer = routing;
        ensureTargets(layer);
        if (!layer.prepared) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(layer.glow.getColorTexture(), new Vector4f(0, 0, 0, 0));
            layer.glow.copyDepthFrom(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            layer.prepared = true;
        }
        layer.used = true;
        return layer.glow;
    }

    public static void composite() {
        if (LAYERS.isEmpty()) return;
        Iterator<Layer> iterator = LAYERS.values().iterator();
        while (iterator.hasNext()) {
            Layer layer = iterator.next();
            boolean used = layer.used;
            layer.prepared = false;
            layer.used = false;
            if (used && layer.glow != null) {
                layer.idleFrames = 0;
                composite(layer);
            } else if (++layer.idleFrames > IDLE_FRAMES_BEFORE_FREE) {
                layer.close();
                iterator.remove();
            }
        }
    }

    private static void composite(Layer layer) {
        ensurePipelines();
        blur(layer, layer.glow, layer.halfA, 0, 1, 0);
        blur(layer, layer.halfA, layer.halfB, 1, 0, 1);
        blur(layer, layer.halfB, layer.quarterA, 2, 1, 0);
        blur(layer, layer.quarterA, layer.quarterB, 3, 0, 1);

        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_text_glow_composite", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HalfSampler", layer.halfB.getColorTextureView(), linear);
            pass.setUniform("QuarterSampler", layer.quarterB.getColorTextureView(), linear);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void blur(Layer layer, RenderTarget source, RenderTarget target, int index, float dx, float dy) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, 16)
                    .putVec4(dx * layer.spread / target.width, dy * layer.spread / target.height, 0, 0).get();
            encoder.writeToBuffer(layer.steps[index].slice(), data);
        }
        try (RenderPass pass = encoder.createRenderPass(() -> "vector3_text_glow_blur", target.getColorTextureView(),
                Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(blurPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("GlowBlur", layer.steps[index]);
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
    }

    private static void ensureTargets(Layer layer) {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = Math.max(1, main.width), height = Math.max(1, main.height);
        if (layer.glow == null) {
            layer.glow = new TextureTarget("vector3_text_glow", width, height, FORMAT, GpuFormat.D32_FLOAT);
            layer.halfA = new TextureTarget("vector3_text_glow_half_a", half(width), half(height), FORMAT, null);
            layer.halfB = new TextureTarget("vector3_text_glow_half_b", half(width), half(height), FORMAT, null);
            layer.quarterA = new TextureTarget("vector3_text_glow_quarter_a", quarter(width), quarter(height), FORMAT, null);
            layer.quarterB = new TextureTarget("vector3_text_glow_quarter_b", quarter(width), quarter(height), FORMAT, null);
            for (int i = 0; i < layer.steps.length; i++) {
                int index = i;
                layer.steps[i] = RenderSystem.getDevice().createBuffer(() -> "vector3_text_glow_step_" + index,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
            }
        } else if (layer.glow.width != width || layer.glow.height != height) {
            layer.glow.resize(width, height);
            layer.halfA.resize(half(width), half(height));
            layer.halfB.resize(half(width), half(height));
            layer.quarterA.resize(quarter(width), quarter(height));
            layer.quarterB.resize(quarter(width), quarter(height));
        }
    }

    private static int half(int size) {
        return Math.max(1, size / 2);
    }

    private static int quarter(int size) {
        return Math.max(1, size / 4);
    }
}
