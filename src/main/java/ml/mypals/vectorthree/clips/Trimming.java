package ml.mypals.vectorthree.clips;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.AudioKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Keyframes that stand for a stretch of media and can be trimmed at either end or split: clips and audio. The range
 * is in timeline ticks, so pitch is already applied; the keyframe sits at the tick where {@code in} plays.
 */
public final class Trimming {
    public record Range(int in, int out, int total) {
        public int length() {
            return out - in;
        }
    }

    private Trimming() {}

    public static boolean trimmable(Keyframe keyframe) {
        return keyframe instanceof ClipKeyframeType.ClipKeyframe || keyframe instanceof AudioKeyframe;
    }

    public static @Nullable Range range(Keyframe keyframe) {
        if (keyframe instanceof ClipKeyframeType.ClipKeyframe clip) {
            ReplayArchive.Info info = ReplayArchive.read(Path.of(clip.value.source()));
            return new Range(clip.value.in(), clip.value.out(), info == null ? clip.value.out() : info.totalTicks());
        }
        if (keyframe instanceof AudioKeyframe && keyframe instanceof AudioTrim trim) {
            float pitch = ((AudioLevel) keyframe).vector3$pitch();
            int total = Math.round(trim.vector3$audioTicks() / pitch);
            if (total <= 0) return null;
            int in = Math.min(Math.round(trim.vector3$audioIn() / pitch), total - 1);
            int out = trim.vector3$audioLength() < 0 ? total : Math.min(total, in + Math.max(1, Math.round(trim.vector3$audioLength() / pitch)));
            return new Range(in, out, total);
        }
        return null;
    }

    /** A copy of {@code keyframe} playing {@code in}..{@code out} of its media. */
    public static Keyframe withRange(Keyframe keyframe, int in, int out) {
        if (keyframe instanceof ClipKeyframeType.ClipKeyframe clip) {
            return new ClipKeyframeType.ClipKeyframe(clip.value.withRange(in, out), InterpolationType.LINEAR);
        }
        Keyframe copy = keyframe.copy();
        if (copy instanceof AudioTrim trim) {
            Range range = range(keyframe);
            float pitch = ((AudioLevel) keyframe).vector3$pitch();
            trim.vector3$setAudioTrim(Math.round(in * pitch), range != null && out >= range.total() ? -1 : Math.round((out - in) * pitch));
        }
        return copy;
    }
}
