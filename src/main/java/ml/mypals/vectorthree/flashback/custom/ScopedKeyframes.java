package ml.mypals.vectorthree.flashback.custom;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;
import imgui.moulberry90.ImDrawList;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Keyframes that act from one keyframe until the next, stopped by a keyframe marked as ending the
 * scope (Look To, Dolly Zoom). The value is blended from a keyframe toward the next one.
 */
public final class ScopedKeyframes {
    public record Segment<T>(T from, T to, float amount) {}

    private ScopedKeyframes() {}

    /** The segment {@code tick} falls in, eased by the keyframes' interpolation, or null outside any scope. */
    public static <T> @Nullable Segment<T> segment(TreeMap<Integer, Keyframe> keyframes, float tick, Predicate<T> endsScope) {
        Map.Entry<Integer, Keyframe> start = keyframes.floorEntry((int) Math.floor(tick));
        if (start == null) return null;
        T from = CustomKeyframeType.valueOf(start.getValue());
        if (endsScope.test(from)) return null;
        Map.Entry<Integer, Keyframe> end = keyframes.higherEntry(start.getKey());
        if (end == null) return new Segment<>(from, from, 0);
        float progress = Math.clamp((tick - start.getKey()) / (end.getKey() - start.getKey()), 0, 1);
        float amount = SidedInterpolationType.interpolate(start.getValue().interpolationType().rightSide,
                end.getValue().interpolationType().leftSide, progress);
        return new Segment<>(from, CustomKeyframeType.valueOf(end.getValue()), amount);
    }

    /** Draws a scope as a bar to the next keyframe and scope ends as hollow squares; see CustomKeyframeType. */
    public static boolean draw(boolean endsScope, ImDrawList drawList, int size, float x, float y, int colour,
            float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        if (!endsScope) {
            Integer next = keyframes.higherKey(tick);
            float endX = next == null ? maxX : x + (next - tick) / ticksPerPixel;
            float left = Math.max(x, minX);
            float right = Math.min(endX, maxX);
            if (right > left) drawList.addLine(left, y, right, y, (colour & 0x00FFFFFF) | 0x99000000, size * 0.5f);
            return false;
        }
        drawList.addRect(x - size, y - size, x + size, y + size, colour, 0, 0, 2);
        return true;
    }
}
