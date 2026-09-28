package ml.mypals.vectorthree.shape.area;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.Optional;

final class AreaRenderType {
    private static RenderType translucentType;
    private static RenderType solidType;
    private static RenderType cutoutType;
    private static RenderType outlineType;
    private static RenderType ownTranslucent;
    private static RenderType ownSolid;
    private static RenderType ownCutout;
    private static final RenderType[] skinned = new RenderType[3];
    private static final BindGroupLayout SKIN_LAYOUT = BindGroupLayout.builder()
            .withUniform("BlastBlockOf", UniformType.TEXEL_BUFFER, GpuFormat.R32_SINT)
            .withUniform("BlastTransforms", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .build();

    private AreaRenderType() {}

    static RenderType get() {
        if (translucentType == null) translucentType = create("vector3_area_translucent", RenderPipelines.TRANSLUCENT_BLOCK);
        return translucentType;
    }

    static RenderType getSolid() {
        if (solidType == null) solidType = create("vector3_area_solid", RenderPipelines.SOLID_BLOCK);
        return solidType;
    }

    /** Alpha-tested (leaves, glass panes, ...) — SOLID_BLOCK doesn't discard, so these need their own pipeline. */
    static RenderType getCutout() {
        if (cutoutType == null) cutoutType = create("vector3_area_cutout", RenderPipelines.CUTOUT_BLOCK);
        return cutoutType;
    }

    /**
     * The same three with the core/area_block vertex shader, whose fog reads the destination position from
     * TextureMat. Vanilla's block shader takes the fog distance from the raw vertex position, which for the area's
     * mesh is its source region's world position, so without a shader pack the whole area drowned in fog colour.
     * Iris only knows the vanilla pipelines, so a shaded world pass keeps those.
     */
    static RenderType get(boolean own) {
        if (!own) return get();
        if (ownTranslucent == null) ownTranslucent = create("vector3_area_translucent_fog", derive(RenderPipelines.TRANSLUCENT_BLOCK, "translucent"));
        return ownTranslucent;
    }

    static RenderType getSolid(boolean own) {
        if (!own) return getSolid();
        if (ownSolid == null) ownSolid = create("vector3_area_solid_fog", derive(RenderPipelines.SOLID_BLOCK, "solid"));
        return ownSolid;
    }

    static RenderType getCutout(boolean own) {
        if (!own) return getCutout();
        if (ownCutout == null) ownCutout = create("vector3_area_cutout_fog", derive(RenderPipelines.CUTOUT_BLOCK, "cutout"));
        return ownCutout;
    }

    /** BlastShape's GPU-skinned block layers (0 solid, 1 cutout, 2 translucent), with core/blast_skin. */
    static RenderType skinned(int layer) {
        if (skinned[layer] == null) {
            RenderPipeline base = layer == 0 ? RenderPipelines.SOLID_BLOCK : layer == 1 ? RenderPipelines.CUTOUT_BLOCK
                    : RenderPipelines.TRANSLUCENT_BLOCK;
            String name = layer == 0 ? "solid" : layer == 1 ? "cutout" : "translucent";
            skinned[layer] = create("vector3_blast_skin_" + name, derive(base, "blast_skin_" + name, "core/blast_skin", SKIN_LAYOUT));
        }
        return skinned[layer];
    }

    private static RenderType create(String name, RenderPipeline pipeline) {
        RenderSetup setup = RenderSetup.builder(pipeline)
                .useLightmap()
                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                .affectsCrumbling()
                .createRenderSetup();
        return RenderType.create(name, setup);
    }

    private static RenderPipeline derive(RenderPipeline base, String name) {
        return derive(base, "area_" + name, "core/area_block", null);
    }

    private static RenderPipeline derive(RenderPipeline base, String name, String vertexShader, BindGroupLayout extra) {
        java.util.List<BindGroupLayout> layouts = new java.util.ArrayList<>(base.getBindGroupLayouts());
        if (extra != null) layouts.add(extra);
        RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(base.getShaders(), Optional.of(base.getShaderDefines()),
                Optional.of(layouts), base.getColorTargetStates().toArray(new ColorTargetState[0]),
                base.getColorTargetStates().size(), Optional.ofNullable(base.getDepthStencilState()),
                Optional.of(base.getPolygonMode()), Optional.of(base.isCull()),
                base.getVertexFormatBindings().toArray(new VertexFormat[0]), Optional.of(base.getPrimitiveTopology()),
                base.pushConstantSize());
        return RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Vector3.id("pipeline/" + name))
                .withVertexShader(Vector3.id(vertexShader))
                .build());
    }

    /**
     * Vanilla's own glow/outline RenderType — the same one that outlines glowing mobs. Its pipeline
     * (RenderPipelines.OUTLINE_CULL/NO_CULL) uses DefaultVertexFormat.POSITION_TEX_COLOR, not the
     * richer block format the solid/cutout/translucent pipelines above use, so this needs its own
     * bake (see AreaBaker's outline output) rather than reusing those buffers.
     */
    static RenderType getOutline() {
        if (outlineType == null) outlineType = RenderTypes.outline(TextureAtlas.LOCATION_BLOCKS);
        return outlineType;
    }
}
