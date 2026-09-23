package ml.mypals.vectorthree.mixin.iris.skyOverride;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Debug(export = true)
@Mixin(IrisPipelines.class)
public class PropertiesMixin {
    @WrapMethod(method = "getPipeline", remap = false)
    private static ShaderKey isSky(IrisRenderingPipeline pipeline, com.mojang.renderpearl.api.pipeline.RenderPipeline shader, Operation<ShaderKey> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        ShaderKey key = original.call(pipeline, shader);
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;
            if(!visuals.renderSky && IrisApi.getInstance().isShaderPackInUse() && isBlackListed(key)){
                return ShaderKey.BASIC;
            }
        }
        return key;
    }
    @Unique
    private static boolean isBlackListed(ShaderKey key){
        return key == ShaderKey.SKY_BASIC
                || key == ShaderKey.SKY_TEXTURED
                || key == ShaderKey.SKY_TEXTURED_COLOR
                || key == ShaderKey.SKY_BASIC_COLOR
                || key == ShaderKey.CLOUDS
                || key == ShaderKey.CLOUDS_SODIUM
                || key == ShaderKey.SHADOW_CLOUDS
                || key == ShaderKey.WEATHER;
    }
}
