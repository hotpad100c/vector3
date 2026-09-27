package ml.mypals.vectorthree.shape.area;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
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

    private static RenderType create(String name, RenderPipeline pipeline) {
        RenderSetup setup = RenderSetup.builder(pipeline)
                .useLightmap()
                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                .affectsCrumbling()
                .createRenderSetup();
        return RenderType.create(name, setup);
    }

    private static RenderPipeline derive(RenderPipeline base, String name) {
        RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(base.getShaders(), Optional.of(base.getShaderDefines()),
                Optional.of(base.getBindGroupLayouts()), base.getColorTargetStates().toArray(new ColorTargetState[0]),
                base.getColorTargetStates().size(), Optional.ofNullable(base.getDepthStencilState()),
                Optional.of(base.getPolygonMode()), Optional.of(base.isCull()),
                base.getVertexFormatBindings().toArray(new VertexFormat[0]), Optional.of(base.getPrimitiveTopology()),
                base.pushConstantSize());
        return RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Vector3.id("pipeline/area_" + name))
                .withVertexShader(Vector3.id("core/area_block"))
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
