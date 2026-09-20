package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;

public record ShapeKeyframeChange(ShapeState state) implements KeyframeChange {
    @Override
    public void apply(KeyframeHandler handler) {
        ShapeTrackRegistry.apply(state);
    }

    @Override
    public KeyframeChange interpolate(KeyframeChange other, double amount) {
        return other instanceof ShapeKeyframeChange target
                ? new ShapeKeyframeChange(state.interpolate(target.state, amount)) : this;
    }
}
