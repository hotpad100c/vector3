package ml.mypals.vectorthree.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.vertices.ImmediateState;

// Links against Iris; only reached through IrisCompat once Iris is known to be loaded.
final class IrisHooks {
    private IrisHooks() {}

    static boolean isPackInUse() {
        return Iris.isPackInUseQuick();
    }

    static void assignPipeline(RenderPipeline pipeline, IrisCompat.Program program) {
        IrisApi.getInstance().assignPipeline(pipeline, switch (program) {
            case ENTITIES_TRANSLUCENT -> IrisProgram.ENTITIES_TRANSLUCENT;
            case PARTICLES_TRANSLUCENT -> IrisProgram.PARTICLES_TRANSLUCENT;
            case LINES -> IrisProgram.LINES;
        });
    }

    static boolean bypass() { return ImmediateState.bypass; }
    static void setBypass(boolean bypass) { ImmediateState.bypass = bypass; }
    static boolean isRenderingLevel() { return ImmediateState.isRenderingLevel; }
    static void setRenderingLevel(boolean renderingLevel) { ImmediateState.isRenderingLevel = renderingLevel; }
    static boolean skipExtension() { return ImmediateState.skipExtension.get(); }
    static void setSkipExtension(boolean skip) { ImmediateState.skipExtension.set(skip); }
}
