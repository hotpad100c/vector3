package ml.mypals.vectorthree.flashback.curve;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.moulberry.flashback.keyframe.Keyframe;
import ml.mypals.vectorthree.Vector3;
import org.jetbrains.annotations.Nullable;

public final class SpeedCurves {
    private static final Gson GSON = new Gson();
    private static final String FIELD = "vector3_curve";

    private SpeedCurves() {}

    public static @Nullable SpeedCurve of(Keyframe keyframe) {
        return ((CurveHolder) keyframe).vector3$curve();
    }

    public static void set(Keyframe keyframe, @Nullable SpeedCurve curve) {
        ((CurveHolder) keyframe).vector3$setCurve(curve == null ? null : curve.sanitized());
    }

    public static void write(Keyframe keyframe, JsonObject json) {
        SpeedCurve curve = of(keyframe);
        if (curve != null) json.add(FIELD, GSON.toJsonTree(curve));
    }

    public static void read(Keyframe keyframe, JsonObject json) {
        if (!json.has(FIELD)) return;
        try {
            set(keyframe, GSON.fromJson(json.get(FIELD), SpeedCurve.class));
        } catch (RuntimeException exception) {
            Vector3.LOGGER.warn("Dropping an unreadable keyframe speed curve", exception);
        }
    }
}
