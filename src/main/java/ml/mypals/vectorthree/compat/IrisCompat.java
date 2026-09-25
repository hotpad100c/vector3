package ml.mypals.vectorthree.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The only way the rest of the mod reaches Iris. Every call checks {@link #LOADED} first, so IrisHooks
 * (which links against Iris) is never loaded when Iris isn't installed.
 */
public final class IrisCompat {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("iris");

    public enum Program { ENTITIES_TRANSLUCENT, PARTICLES_TRANSLUCENT, LINES }

    private IrisCompat() {}

    public static boolean isPackInUse() {
        return LOADED && IrisHooks.isPackInUse();
    }

    public static void assignPipeline(RenderPipeline pipeline, Program program) {
        if (LOADED) IrisHooks.assignPipeline(pipeline, program);
    }

    public static boolean bypass() {
        return LOADED && IrisHooks.bypass();
    }

    public static void setBypass(boolean bypass) {
        if (LOADED) IrisHooks.setBypass(bypass);
    }

    public static boolean isRenderingLevel() {
        return LOADED && IrisHooks.isRenderingLevel();
    }

    public static void setRenderingLevel(boolean renderingLevel) {
        if (LOADED) IrisHooks.setRenderingLevel(renderingLevel);
    }

    public static boolean skipExtension() {
        return LOADED && IrisHooks.skipExtension();
    }

    public static void setSkipExtension(boolean skip) {
        if (LOADED) IrisHooks.setSkipExtension(skip);
    }
}
