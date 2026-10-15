package ml.mypals.vectorthree.mc.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import ml.mypals.vectorthree.mc.shape.model.ObjPbrTexture;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.api.v0.IrisShadowProgram;
import net.irisshaders.iris.api.v0.IrisShadowRenderCallback;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import net.irisshaders.iris.vertices.ImmediateState;

// Links against Iris; only reached through IrisCompat once Iris is known to be loaded.
final class IrisHooks {
    private IrisHooks() {}

    // Iris looks PBR maps up by the texture's class; OBJ textures carry theirs (see ObjPbrTexture).
    static void registerObjPbr() {
        PBRTextureLoaderRegistry.INSTANCE.register(
                ObjPbrTexture.class, (texture, resources, consumer) -> {
                    var normal = texture.loadNormal();
                    if (normal != null) consumer.acceptNormalTexture(normal);
                    var specular = texture.loadSpecular();
                    if (specular != null) consumer.acceptSpecularTexture(specular);
                });
    }

    static boolean isRenderingShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

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

    // Iris 0.4 introduced the shadow pass API; on anything older these are no-ops, so the shape simply casts no
    // shadow instead of the mod failing to load.
    static void assignPipelineShadow(RenderPipeline pipeline, IrisCompat.ShadowProgram program) {
        if (IrisApi.getInstance().getMinorApiRevision() < 4) return;
        IrisApi.getInstance().assignPipelineShadow(pipeline, switch (program) {
            case ENTITIES -> IrisShadowProgram.SHADOW_ENTITIES;
        });
    }

    // The callback is invoked from Iris's shadow pass, so the wrapper only forwards; the hook itself is kept free
    // of Iris types because IrisCompat may be loaded without Iris.
    static void registerShadowRenderCallback(IrisCompat.ObjShadowHook hook) {
        if (IrisApi.getInstance().getMinorApiRevision() < 4) return;
        IrisApi.getInstance().registerShadowRenderCallback(
                (modelView, projection, cameraX, cameraY, cameraZ, tickDelta) ->
                        hook.renderShadow(modelView, projection, cameraX, cameraY, cameraZ, tickDelta));
    }

    static boolean bypass() { return ImmediateState.bypass; }
    static void setBypass(boolean bypass) { ImmediateState.bypass = bypass; }
    static boolean isRenderingLevel() { return ImmediateState.isRenderingLevel; }
    static void setRenderingLevel(boolean renderingLevel) { ImmediateState.isRenderingLevel = renderingLevel; }
    static boolean skipExtension() { return ImmediateState.skipExtension.get(); }
    static void setSkipExtension(boolean skip) { ImmediateState.skipExtension.set(skip); }
}
