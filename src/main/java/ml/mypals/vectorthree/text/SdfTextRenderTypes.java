package ml.mypals.vectorthree.text;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
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
        IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.ENTITIES_TRANSLUCENT);
        return pipeline;
    }
}
