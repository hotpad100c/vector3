package ml.mypals.vectorthree.flashback.custom;

import ml.mypals.vectorthree.flashback.curve.SpeedCurves;
import ml.mypals.vectorthree.prefab.PrefabGroups;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import imgui.moulberry90.ImDrawList;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A keyframe of a {@link CustomKeyframeType}. The value is immutable and replaced on edit. Types may
 * subclass this (see {@link CustomKeyframeType#newKeyframe}) so the rest of the code can match on it.
 */
public class CustomKeyframe<T> extends Keyframe {
    private final CustomKeyframeType<T> type;
    public T value;

    public CustomKeyframe(CustomKeyframeType<T> type, T value) {
        this(type, value, InterpolationType.getDefault());
    }

    public CustomKeyframe(CustomKeyframeType<T> type, T value, InterpolationType interpolation) {
        this.type = type;
        this.value = value;
        interpolationType(interpolation);
    }

    public CustomKeyframeType<T> type() {
        return type;
    }

    @Override public final KeyframeType<?> keyframeType() { return type; }
    @Override
    public final Keyframe copy() {
        Keyframe copy = type.newKeyframe(value, interpolationType());
        PrefabGroups.tag(copy, PrefabGroups.groupOf(this));
        SpeedCurves.set(copy, SpeedCurves.of(this));
        return copy;
    }
    @Override public final KeyframeChange createChange() { return CustomKeyframeChange.of(type, value); }

    @Override
    public final KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3,
            float t0, float t1, float t2, float t3, float amount) {
        return CustomKeyframeChange.of(type, type.smooth(value, CustomKeyframeType.valueOf(p1),
                CustomKeyframeType.valueOf(p2), CustomKeyframeType.valueOf(p3), t1 - t0, t2 - t0, t3 - t0, amount));
    }

    @Override
    public final KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float tick) {
        Map<Float, T> values = new HashMap<>();
        keyframes.forEach((time, keyframe) -> values.put(time, CustomKeyframeType.valueOf(keyframe)));
        return CustomKeyframeChange.of(type, type.hermite(values, tick));
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        T edited = type.edit(value);
        if (edited != value) update.accept(keyframe -> ((CustomKeyframe<T>) keyframe).value = edited);
    }

    @Override
    public final void drawOnTimeline(ImDrawList drawList, int size, float x, float y, int colour, float ticksPerPixel,
            float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        if (!type.drawOnTimeline(this, drawList, size, x, y, colour, ticksPerPixel, minX, maxX, tick, keyframes)) {
            super.drawOnTimeline(drawList, size, x, y, colour, ticksPerPixel, minX, maxX, tick, keyframes);
        }
    }
}
