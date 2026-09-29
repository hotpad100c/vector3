package ml.mypals.vectorthree.core.clips;

import java.util.ArrayList;
import java.util.List;

/** Implemented on Flashback's AudioKeyframe: a volume fade at each end, and the beats found in the file. */
public interface AudioEnvelope {
    /** Timeline ticks the volume takes to rise from silence at the start. */
    int vector3$fadeIn();

    /** Timeline ticks the volume takes to fall to silence at the end. */
    int vector3$fadeOut();

    void vector3$setFades(int in, int out);

    /** Beats as ticks into the file, ascending. */
    int[] vector3$beats();

    void vector3$setBeats(int[] beats);

    /** Finds the beats in the loaded file, or none when it failed to load. */
    int[] vector3$detectBeats();

    /** The gain the fades give {@code elapsed} ticks into a keyframe that plays for {@code width} ticks. */
    static float gain(int fadeIn, int fadeOut, float elapsed, float width) {
        float gain = 1;
        if (fadeIn > 0) gain = Math.min(gain, elapsed / fadeIn);
        if (fadeOut > 0 && width > 0) gain = Math.min(gain, (width - elapsed) / fadeOut);
        return Math.clamp(gain, 0, 1);
    }

    /** Ticks into the file of beats that a drum-like jump in loudness lands on, at least 4 ticks apart. */
    static int[] detect(byte[] waveform, float durationTicks) {
        int length = waveform.length;
        if (length < 16 || durationTicks <= 0) return new int[0];
        float[] level = new float[length];
        for (int i = 0; i < length; i++) level[i] = (waveform[i] + 128) / 255f;
        float perBucket = durationTicks / length;
        int back = Math.max(4, Math.round(3 / perBucket));
        int spacing = Math.max(1, Math.round(4 / perBucket));
        List<Integer> beats = new ArrayList<>();
        int last = -spacing;
        for (int i = back; i < length - 1; i++) {
            float mean = 0;
            for (int k = i - back; k < i; k++) mean += level[k];
            mean /= back;
            boolean peak = level[i] >= level[i - 1] && level[i] > level[i + 1];
            if (peak && level[i] > mean * 1.6f + 0.06f && i - last >= spacing) {
                beats.add(Math.round(i * perBucket));
                last = i;
            }
        }
        return beats.stream().mapToInt(Integer::intValue).toArray();
    }
}
