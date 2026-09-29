package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.fb.expression.EvalContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EditorState.class, remap = false)
public class EditorStateExpressionMixin {
    @WrapMethod(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V")
    private void vector3$expressionContext(KeyframeHandler handler, float tick, long stamp, Operation<Void> original) {
        EvalContext.begin(tick);
        try {
            original.call(handler, tick, stamp);
        } finally {
            EvalContext.end();
        }
    }

    @ModifyExpressionValue(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V",
            at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/state/EditorState;currentScene()Lcom/moulberry/flashback/state/EditorScene;"))
    private EditorScene vector3$expressionScene(EditorScene scene) {
        EvalContext.scene(scene);
        return scene;
    }
}
