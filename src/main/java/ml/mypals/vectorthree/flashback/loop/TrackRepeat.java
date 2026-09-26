package ml.mypals.vectorthree.flashback.loop;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.KeyframeTrack;
import org.jetbrains.annotations.Nullable;

import java.util.TreeMap;

/** What a track does after its last keyframe: hold (Flashback's default), loop, or ping-pong back and forth. */
public enum TrackRepeat {
    NONE, LOOP, PING_PONG;

    public interface Holder {
        @Nullable TrackRepeat vector3$repeat();

        void vector3$setRepeat(@Nullable TrackRepeat repeat);
    }

    public static TrackRepeat of(KeyframeTrack track) {
        TrackRepeat repeat = ((Holder) track).vector3$repeat();
        return repeat == null ? NONE : repeat;
    }

    public static void set(KeyframeTrack track, TrackRepeat repeat) {
        ((Holder) track).vector3$setRepeat(repeat == NONE ? null : repeat);
    }

    public String translationKey() {
        return "vector3.repeat." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The tick inside the keyframes' span that {@code tick} plays, past the last keyframe. */
    public float remap(TreeMap<Integer, Keyframe> keyframes, float tick) {
        if (this == NONE || keyframes.size() < 2) return tick;
        int first = keyframes.firstKey(), last = keyframes.lastKey();
        float span = last - first;
        if (tick <= last || span <= 0) return tick;
        double offset = tick - first;
        long cycle = (long) Math.floor(offset / span);
        float within = (float) (offset - cycle * span);
        if (this == PING_PONG && (cycle & 1) == 1) return last - within;
        return first + within;
    }
}
