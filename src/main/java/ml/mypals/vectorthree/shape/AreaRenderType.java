package ml.mypals.vectorthree.shape;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

final class AreaRenderType {
    private static RenderType type;

    private AreaRenderType() {}

    static RenderType get() {
        if (type == null) {
            RenderSetup setup = RenderSetup.builder(RenderPipelines.TRANSLUCENT_BLOCK)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .affectsCrumbling()
                    .createRenderSetup();
            type = RenderType.create("vector3_area_translucent", setup);
        }
        return type;
    }
}
