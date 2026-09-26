package ml.mypals.vectorthree.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
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
 * Bloom for text: glowing text is drawn a second time into an HDR buffer, which goes down a mip chain and
 * back up, then is added onto the main target once per frame. The spread picks how deep the chain reaches,
 * continuously between levels. Each spread (in quarter steps) keeps its own buffers.
 */
public final class TextGlow {
    public static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int IDLE_FRAMES_BEFORE_FREE = 5;
    private static final int LEVELS = 7;
    // How much of the wider level each upsample keeps; lower keeps the glow tighter to the text.
    private static final float SCATTER = 0.7f;
    // Spread 1 is a glow radius of about this many pixels at 1080p.
    private static final float PIXELS_PER_SPREAD = 6;

    private static final class Layer {
        final float spread;
        RenderTarget glow;
        final RenderTarget[] down = new RenderTarget[LEVELS], up = new RenderTarget[LEVELS];
        final GpuBuffer[] steps = new GpuBuffer[LEVELS * 2];
        boolean prepared, used;
        int idleFrames;

        Layer(float spread) {
            this.spread = spread;
        }

        void close() {
            if (glow != null) glow.destroyBuffers();
            for (RenderTarget[] targets : new RenderTarget[][]{down, up}) {
                for (RenderTarget target : targets) {
                    if (target != null) target.destroyBuffers();
                }
            }
            for (GpuBuffer step : steps) {
                if (step != null) step.close();
            }
        }
    }

    private static final Map<Integer, Layer> LAYERS = new HashMap<>();
    private static RenderPipeline downPipeline;
    private static RenderPipeline upPipeline;
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
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        float radius = layer.spread * PIXELS_PER_SPREAD * main.height / 1080f;
        float depth = Math.clamp((float) (Math.log(Math.max(radius, 1)) / Math.log(2)), 1, LEVELS);
        int whole = (int) depth;
        float fraction = depth - whole;
        int top = fraction > 0.01f && whole < LEVELS ? whole + 1 : whole;

        RenderTarget source = layer.glow;
        for (int level = 0; level < top; level++) {
            down(layer, source, layer.down[level], level);
            source = layer.down[level];
        }
        RenderTarget wider = layer.down[top - 1];
        for (int level = top - 2; level >= 0; level--) {
            float weight = level == top - 2 && top > whole ? SCATTER * fraction : SCATTER;
            up(layer, wider, layer.down[level], layer.up[level], level, weight);
            wider = layer.up[level];
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_text_glow_composite", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", wider.getColorTextureView(), linear());
            pass.draw(3, 1, 0, 0);
        }
    }

    private static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    private static void writeStep(GpuBuffer buffer, float x, float y, float z) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, 16).putVec4(x, y, z, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    private static void down(Layer layer, RenderTarget source, RenderTarget target, int level) {
        GpuBuffer step = layer.steps[level];
        writeStep(step, 1f / source.width, 1f / source.height, 0);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_text_glow_down", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(downPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source.getColorTextureView(), linear());
            pass.setUniform("GlowStep", step);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void up(Layer layer, RenderTarget wider, RenderTarget current, RenderTarget target, int level, float weight) {
        GpuBuffer step = layer.steps[LEVELS + level];
        writeStep(step, 1f / wider.width, 1f / wider.height, weight);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_text_glow_up", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(upPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("PrevSampler", wider.getColorTextureView(), linear());
            pass.setUniform("CurrentSampler", current.getColorTextureView(), linear());
            pass.setUniform("GlowStep", step);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static RenderPipeline screenPass(String name, BindGroupLayout layout, ColorTargetState target) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Vector3.id("pipeline/text_glow_" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Vector3.id("post/glow_" + name))
                .withBindGroupLayout(layout)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(target)
                .withDepthStencilState(Optional.empty())
                .build());
    }

    private static void ensurePipelines() {
        if (downPipeline != null) return;
        ColorTargetState hdr = new ColorTargetState(Optional.empty(), FORMAT, ColorTargetState.WRITE_ALL);
        downPipeline = screenPass("down", BindGroupLayout.builder()
                .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("GlowStep", UniformType.UNIFORM_BUFFER)
                .build(), hdr);
        upPipeline = screenPass("up", BindGroupLayout.builder()
                .withUniform("PrevSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("CurrentSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("GlowStep", UniformType.UNIFORM_BUFFER)
                .build(), hdr);
        compositePipeline = screenPass("composite", BindGroupLayout.builder()
                .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .build(), new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_COLOR));
    }

    private static void ensureTargets(Layer layer) {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = Math.max(1, main.width), height = Math.max(1, main.height);
        if (layer.glow == null) {
            layer.glow = new TextureTarget("vector3_text_glow", width, height, FORMAT, GpuFormat.D32_FLOAT);
            for (int level = 0; level < LEVELS; level++) {
                int w = scaled(width, level), h = scaled(height, level);
                layer.down[level] = new TextureTarget("vector3_text_glow_down_" + level, w, h, FORMAT, null);
                layer.up[level] = new TextureTarget("vector3_text_glow_up_" + level, w, h, FORMAT, null);
            }
            for (int i = 0; i < layer.steps.length; i++) {
                int index = i;
                layer.steps[i] = RenderSystem.getDevice().createBuffer(() -> "vector3_text_glow_step_" + index,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
            }
        } else if (layer.glow.width != width || layer.glow.height != height) {
            layer.glow.resize(width, height);
            for (int level = 0; level < LEVELS; level++) {
                layer.down[level].resize(scaled(width, level), scaled(height, level));
                layer.up[level].resize(scaled(width, level), scaled(height, level));
            }
        }
    }

    private static int scaled(int size, int level) {
        return Math.max(1, size >> (level + 1));
    }
}
