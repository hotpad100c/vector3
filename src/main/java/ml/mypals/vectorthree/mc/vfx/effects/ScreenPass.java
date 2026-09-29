package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.Mod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Optional;

public final class ScreenPass {
    private static RenderTarget scratch;
    private static RenderPipeline copyPipeline;

    private ScreenPass() {}

    public static RenderTarget scratch(RenderTarget main) {
        if (scratch == null) scratch = new TextureTarget("vector3_vfx_scratch", main.width, main.height,
                GpuFormat.RGBA8_UNORM, null);
        else if (scratch.width != main.width || scratch.height != main.height) scratch.resize(main.width, main.height);
        return scratch;
    }

    public static void copy(RenderTarget source, RenderTarget target) {
        if (copyPipeline == null) copyPipeline = pipeline("screen_vfx_copy", BindGroupLayout.builder()
                .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER).build(),
                GpuFormat.RGBA8_UNORM, null);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_vfx_copy", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(copyPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source.getColorTextureView(), linear());
            pass.draw(3, 1, 0, 0);
        }
    }

    public static RenderPipeline pipeline(String name, BindGroupLayout layout, GpuFormat format,
                                          BlendFunction blend) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Mod.id("pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Mod.id("post/" + name))
                .withBindGroupLayout(layout)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(new ColorTargetState(Optional.ofNullable(blend), format,
                        ColorTargetState.WRITE_COLOR))
                .withDepthStencilState(Optional.empty()).build());
    }

    public static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
