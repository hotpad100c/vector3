package ml.mypals.vectorthree.shape;

import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import java.util.function.Consumer;

/** Optional hook for a future visual shape manager. Core timeline support has no UI dependency. */
@FunctionalInterface
public interface ShapeTrackEditor {
    void edit(ShapeKeyframe keyframe, Consumer<Consumer<ShapeKeyframe>> update);
}
