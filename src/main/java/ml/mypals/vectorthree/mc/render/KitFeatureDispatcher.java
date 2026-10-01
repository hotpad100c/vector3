package ml.mypals.vectorthree.mc.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

/**
 * The feature dispatcher RRK's Helpers#renderFeatures draws with (see HelpersIrisBypassMixin). The game's dispatcher
 * has a single PreparedFrame, which 26.2's LevelRenderer keeps open until its frame graph has run, and RRK draws from
 * END_MAIN inside that graph.
 */
public final class KitFeatureDispatcher {
    private static RenderBuffers buffers;
    private static FeatureRenderDispatcher dispatcher;

    private KitFeatureDispatcher() {}

    public static FeatureRenderDispatcher get() {
        if (dispatcher == null) {
            Minecraft minecraft = Minecraft.getInstance();
            buffers = new RenderBuffers(1);
            dispatcher = new FeatureRenderDispatcher(buffers, minecraft.getModelManager(), minecraft.getAtlasManager(),
                    minecraft.font, minecraft.gameRenderer.gameRenderState());
        }
        return dispatcher;
    }

    /** Once a frame, after the last draw, so the staged vertex buffer can recycle the GPU buffers the frame used. */
    public static void endFrame() {
        if (buffers != null) buffers.endFrame();
    }
}
