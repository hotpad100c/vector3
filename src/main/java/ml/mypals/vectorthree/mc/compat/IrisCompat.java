package ml.mypals.vectorthree.mc.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;

/**
 * The only way the rest of the mod reaches Iris. Every call checks {@link #LOADED} first, so IrisHooks
 * (which links against Iris) is never loaded when Iris isn't installed.
 */
public final class IrisCompat {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("iris");

    public enum Program { ENTITIES_TRANSLUCENT, PARTICLES_TRANSLUCENT, LINES }

    /** The Iris programs the shadow pass can draw our geometry with. */
    public enum ShadowProgram { ENTITIES }

    /**
     * Draws one frame of custom geometry into Iris's shadow map. Called from Iris's shadow pass, after the opaque
     * terrain: the matrices are the shadow pass's own, and anything drawn ends up in the shadow framebuffer.
     */
    @FunctionalInterface
    public interface ObjShadowHook {
        void renderShadow(Matrix4f modelView, Matrix4f projection, double cameraX, double cameraY, double cameraZ,
                float tickDelta);
    }

    private IrisCompat() {}

    public static void registerObjPbr() {
        if (LOADED) IrisHooks.registerObjPbr();
    }

    /** Iris renders the world again from the sun; entities there are posed relative to the shadow camera. */
    public static boolean isRenderingShadowPass() {
        return LOADED && IrisHooks.isRenderingShadowPass();
    }

    public static boolean isPackInUse() {
        return LOADED && IrisHooks.isPackInUse();
    }

    public static void assignPipeline(RenderPipeline pipeline, Program program) {
        if (LOADED) IrisHooks.assignPipeline(pipeline, program);
    }

    /** Tells Iris which of its shadow programs draws {@code pipeline} during the shadow pass. */
    public static void assignPipelineShadow(RenderPipeline pipeline, ShadowProgram program) {
        if (LOADED) IrisHooks.assignPipelineShadow(pipeline, program);
    }

    /** Registers a draw hook for the shadow pass; only ever fires while a pack is in use. */
    public static void registerShadowCallback(ObjShadowHook hook) {
        if (LOADED) IrisHooks.registerShadowRenderCallback(hook);
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
