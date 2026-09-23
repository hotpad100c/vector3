package ml.mypals.vectorthree.shape;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;

final class AreaRenderType {
    private static RenderType translucentType;
    private static RenderType solidType;
    private static RenderType cutoutType;
    private static RenderType outlineType;

    private AreaRenderType() {}

    static RenderType get() {
        if (translucentType == null) {
            RenderSetup setup = RenderSetup.builder(RenderPipelines.TRANSLUCENT_BLOCK)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .affectsCrumbling()
                    .createRenderSetup();
            translucentType = RenderType.create("vector3_area_translucent", setup);
        }
        return translucentType;
    }

    static RenderType getSolid() {
        if (solidType == null) {
            RenderSetup setup = RenderSetup.builder(RenderPipelines.SOLID_BLOCK)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .affectsCrumbling()
                    .createRenderSetup();
            solidType = RenderType.create("vector3_area_solid", setup);
        }
        return solidType;
    }

    /** Alpha-tested (leaves, glass panes, ...) — SOLID_BLOCK doesn't discard, so these need their own pipeline. */
    static RenderType getCutout() {
        if (cutoutType == null) {
            RenderSetup setup = RenderSetup.builder(RenderPipelines.CUTOUT_BLOCK)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .affectsCrumbling()
                    .createRenderSetup();
            cutoutType = RenderType.create("vector3_area_cutout", setup);
        }
        return cutoutType;
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
