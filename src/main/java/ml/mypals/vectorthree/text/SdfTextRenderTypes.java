package ml.mypals.vectorthree.text;

import ml.mypals.vectorthree.compat.IrisCompat;
import java.util.Optional;
import ml.mypals.vectorthree.render.TextGlow;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Render types for SDF text. The PARTICLE vertex format is used because Iris never widens it (it only
 * extends BLOCK/ENTITY/glyph formats), so the layout stays fixed when drawn through IrisBypassTarget.
 * Sampled with linear filtering, which the distance field needs to reconstruct smooth edges.
 */
final class SdfTextRenderTypes {
    private static RenderPipeline normalPipeline;
    private static RenderPipeline seeThroughPipeline;
    private static final Map<Identifier, RenderType> NORMAL = new HashMap<>();
    private static final Map<Identifier, RenderType> SEE_THROUGH = new HashMap<>();
    private static RenderPipeline glowPipeline;
    private static RenderPipeline glowSeeThroughPipeline;
    private static final Map<Identifier, RenderType> GLOW = new HashMap<>();
    private static final Map<Identifier, RenderType> GLOW_SEE_THROUGH = new HashMap<>();

    private SdfTextRenderTypes() {}

    static RenderType get(Identifier atlasPage, boolean seeThrough) {
        if (normalPipeline == null) {
            normalPipeline = register("sdf_text", DepthStencilState.DEFAULT);
            seeThroughPipeline = register("sdf_text_see_through", new DepthStencilState(CompareOp.ALWAYS_PASS, true));
        }
        Map<Identifier, RenderType> cache = seeThrough ? SEE_THROUGH : NORMAL;
        return cache.computeIfAbsent(atlasPage, page -> RenderType.create(
                seeThrough ? "vector3_sdf_text_see_through" : "vector3_sdf_text",
                RenderSetup.builder(seeThrough ? seeThroughPipeline : normalPipeline)
                        .withTexture("Sampler0", page, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    // Drawn into TextGlow's HDR buffer: tested against the copied scene depth but never writing it.
    static RenderType getGlow(Identifier atlasPage, boolean seeThrough) {
        if (glowPipeline == null) {
            glowPipeline = registerGlow("sdf_text_glow", DepthStencilState.DEFAULT.depthTest());
            glowSeeThroughPipeline = registerGlow("sdf_text_glow_see_through", CompareOp.ALWAYS_PASS);
        }
        Map<Identifier, RenderType> cache = seeThrough ? GLOW_SEE_THROUGH : GLOW;
        return cache.computeIfAbsent(atlasPage, page -> RenderType.create(
                seeThrough ? "vector3_sdf_text_glow_see_through" : "vector3_sdf_text_glow",
                RenderSetup.builder(seeThrough ? glowSeeThroughPipeline : glowPipeline)
                        .withTexture("Sampler0", page, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    private static RenderPipeline registerGlow(String name, CompareOp depthTest) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("vector3", name))
                .withVertexShader(Identifier.fromNamespaceAndPath("vector3", "core/sdf_text"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("vector3", "core/sdf_text_glow"))
                .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), TextGlow.FORMAT,
                        ColorTargetState.WRITE_ALL))
                .withDepthStencilState(new DepthStencilState(depthTest, false))
                .withCull(false)
                .build());
    }

    private static RenderPipeline register(String name, DepthStencilState depth) {
        Identifier shader = Identifier.fromNamespaceAndPath("vector3", "core/sdf_text");
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("vector3", name))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(depth)
                .withCull(false)
                .build());
        IrisCompat.assignPipeline(pipeline, IrisCompat.Program.ENTITIES_TRANSLUCENT);
        return pipeline;
    }
}
