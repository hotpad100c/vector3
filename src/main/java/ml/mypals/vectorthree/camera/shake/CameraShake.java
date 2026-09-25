package ml.mypals.vectorthree.camera.shake;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.RealTimeMapping;
import com.moulberry.flashback.visuals.FastNoiseLite;
import com.moulberry.flashback.visuals.ReplayVisuals;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;


public final class CameraShake {
    public record Sample(float yaw, float pitch, float roll, float right, float up, float forward) {}

    private static final FastNoiseLite NOISE = new FastNoiseLite();
    private static final float LANE = -10000;
    private static final double PHASE_RATE = 2;

    private CameraShake() {}

    public static double[] phases(TreeMap<Integer, Keyframe> keyframes, float tick, @Nullable RealTimeMapping mapping) {
        double[] phase = new double[4];
        Map.Entry<Integer, Keyframe> previous = null;
        for (Map.Entry<Integer, Keyframe> entry : keyframes.entrySet()) {
            float[] frequency = frequencies(entry.getValue());
            if (previous == null) {
                add(phase, frequency, 1, realTime(Math.min(tick, entry.getKey()), mapping));
            } else {
                float start = previous.getKey(), end = entry.getKey();
                double total = realTime(end, mapping) - realTime(start, mapping);
                double elapsed = realTime(Math.min(tick, end), mapping) - realTime(start, mapping);
                float[] from = frequencies(previous.getValue());
                boolean hold = previous.getValue().interpolationType() == InterpolationType.HOLD;
                double u = hold || total <= 0 ? 0 : elapsed / total;
                for (int i = 0; i < 4; i++) phase[i] += elapsed * (from[i] + (frequency[i] - from[i]) * u / 2);
            }
            previous = entry;
            if (tick <= entry.getKey()) return scaled(phase);
        }
        if (previous != null) {
            add(phase, frequencies(previous.getValue()), 1,
                    realTime(tick, mapping) - realTime(previous.getKey(), mapping));
        }
        return scaled(phase);
    }

    private static float[] frequencies(Keyframe keyframe) {
        ShakeKeyframe base = (ShakeKeyframe) keyframe;
        ShakeParams params = ShakeParams.orDefault(((ShakeHolder) keyframe).vector3$shake());
        return new float[]{base.vector3$frequencyX(), base.vector3$frequencyY(), params.rollFrequency(), params.positionFrequency()};
    }

    private static void add(double[] phase, float[] frequency, double scale, double time) {
        for (int i = 0; i < 4; i++) phase[i] += frequency[i] * scale * time;
    }

    private static double[] scaled(double[] phase) {
        for (int i = 0; i < 4; i++) phase[i] *= PHASE_RATE;
        return phase;
    }

    private static double realTime(float tick, @Nullable RealTimeMapping mapping) {
        return mapping == null ? tick : mapping.getRealTime(tick);
    }

    public static @Nullable Sample current() {
        EditorState state = EditorStateManager.getCurrent();
        ReplayServer server = Flashback.getReplayServer();
        if (state == null || server == null || ReplayUI.isMovingCamera()) return null;
        ReplayVisuals visuals = state.replayVisuals;
        if (!visuals.overrideCameraShake) return null;
        ShakeHolder holder = (ShakeHolder) visuals;
        ShakeParams params = ShakeParams.orDefault(holder.vector3$shake());
        double[] phases = holder.vector3$phases();
        if (phases == null) {
            float tick = Flashback.isExporting() ? (float) Flashback.EXPORT_JOB.getCurrentTickDouble() : (float) server.getPartialReplayTick();
            phases = scaled(new double[]{visuals.cameraShakeXFrequency * tick, visuals.cameraShakeYFrequency * tick,
                    params.rollFrequency() * tick, params.positionFrequency() * tick});
        }
        float degrees = (float) (Math.PI * 2 / 360);
        float lane = LANE - params.seed() * 1013;
        return new Sample(
                fbm(params, phases[0], lane, false) * visuals.cameraShakeXAmplitude * degrees,
                fbm(params, phases[1], lane, true) * visuals.cameraShakeYAmplitude * degrees,
                params.rollAmplitude() == 0 ? 0 : fbm(params, phases[2], lane - 2000, false) * params.rollAmplitude() * degrees,
                params.positionX() == 0 ? 0 : fbm(params, phases[3], lane - 4000, false) * params.positionX(),
                params.positionY() == 0 ? 0 : fbm(params, phases[3], lane - 4000, true) * params.positionY(),
                params.positionZ() == 0 ? 0 : fbm(params, phases[3], lane - 6000, false) * params.positionZ());
    }

    // The first octave samples exactly where Flashback does; the rest add finer, weaker detail.
    private static float fbm(ShakeParams params, double phase, float lane, boolean swap) {
        float sum = 0, weight = 1, total = 0;
        double scale = 1;
        for (int octave = 0; octave < params.octaves(); octave++) {
            float t = (float) (phase * scale + octave * 317.0), l = lane - octave * 131;
            sum += weight * (swap ? NOISE.GetNoise(l, t) : NOISE.GetNoise(t, l));
            total += weight;
            weight *= params.roughness();
            scale *= 2;
        }
        return total == 0 ? 0 : sum / total;
    }
}
