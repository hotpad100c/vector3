package ml.mypals.vectorthree.flashback.fade;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
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
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;

/** Draws the Fade track's current colour over the level once it is fully rendered (post effects included). */
public final class FadeOverlay {
    private static @Nullable Fade pending;
    private static @Nullable Fade current;
    private static RenderPipeline pipeline;
    private static GpuBuffer colour;

    private FadeOverlay() {}

    public static void begin(KeyframeHandler handler) {
        if (handler instanceof MinecraftKeyframeHandler) pending = null;
    }

    public static void request(Fade fade) {
        pending = fade;
    }

    public static void finish(KeyframeHandler handler) {
        if (handler instanceof MinecraftKeyframeHandler) current = pending;
    }

    public static void clear() {
        pending = null;
        current = null;
    }

    public static void render() {
        Fade fade = current;
        if (fade == null || fade.opacity() <= 0.001f) return;
        ensurePipeline();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, 16).putVec4(fade.red(), fade.green(), fade.blue(),
                    Math.min(fade.opacity(), 1)).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(colour.slice(), data);
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_fade", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FadeColor", colour);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void ensurePipeline() {
        if (pipeline != null) return;
        pipeline = RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Vector3.id("pipeline/fade"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Vector3.id("post/fade"))
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("FadeColor", UniformType.UNIFORM_BUFFER)
                        .build())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM,
                        ColorTargetState.WRITE_COLOR))
                .withDepthStencilState(Optional.empty())
                .build());
        colour = RenderSystem.getDevice().createBuffer(() -> "vector3_fade_colour",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
    }
}
