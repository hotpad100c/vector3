package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import ml.mypals.vectorthree.expression.ExpressionBinding;
import ml.mypals.vectorthree.expression.ExpressionBindings;
import ml.mypals.vectorthree.expression.ExpressionRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Saved with the track by Flashback's reflective Gson, like the repeat mode. Wrapping the whole method puts the
// expressions after per-channel evaluation and the repeat remap, and gives them the timeline's own tick.
@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackExpressionMixin implements ExpressionBindings.Holder {
    @Unique private ExpressionBinding[] vector3$expressions;

    @Override public ExpressionBinding[] vector3$expressions() { return vector3$expressions; }
    @Override public void vector3$setExpressions(ExpressionBinding[] expressions) { vector3$expressions = expressions; }

    @WrapMethod(method = "createKeyframeChange")
    private KeyframeChange vector3$driveByExpressions(float tick, RealTimeMapping mapping, Operation<KeyframeChange> original) {
        return ExpressionRuntime.apply((KeyframeTrack) (Object) this, tick, mapping, () -> original.call(tick, mapping));
    }
}
