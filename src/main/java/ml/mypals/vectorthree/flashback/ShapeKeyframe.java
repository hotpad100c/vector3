package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackEditor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class ShapeKeyframe extends Keyframe {
    public ShapeState state;
    private static @Nullable ShapeTrackEditor editor;

    public ShapeKeyframe(ShapeState state) {
        this.state = state;
    }

    public ShapeKeyframe(ShapeState state, InterpolationType interpolation) {
        this(state);
        interpolationType(interpolation);
    }

    public static void setEditor(@Nullable ShapeTrackEditor editor) {
        ShapeKeyframe.editor = editor;
    }

    @Override public KeyframeType<?> keyframeType() { return ShapeKeyframeType.INSTANCE; }
    @Override public Keyframe copy() { return new ShapeKeyframe(state, interpolationType()); }
    @Override public KeyframeChange createChange() { return new ShapeKeyframeChange(state); }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3,
            float t0, float t1, float t2, float t3, float amount) {
        return new ShapeKeyframeChange(((ShapeKeyframe) p1).state
                .interpolate(((ShapeKeyframe) p2).state, amount));
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        TreeMap<Float, Keyframe> sorted = new TreeMap<>(keyframes);
        Map.Entry<Float, Keyframe> floor = sorted.floorEntry(amount);
        Map.Entry<Float, Keyframe> ceil = sorted.ceilingEntry(amount);
        if (floor == null) floor = sorted.firstEntry();
        if (ceil == null) ceil = sorted.lastEntry();
        float span = ceil.getKey() - floor.getKey();
        double local = span == 0 ? 0 : (amount - floor.getKey()) / span;
        return new ShapeKeyframeChange(((ShapeKeyframe) floor.getValue()).state
                .interpolate(((ShapeKeyframe) ceil.getValue()).state, local));
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        ShapeTrackEditor current = editor;
        if (current != null) {
            current.edit(this, typed -> update.accept(keyframe -> typed.accept((ShapeKeyframe) keyframe)));
        }
    }
}
