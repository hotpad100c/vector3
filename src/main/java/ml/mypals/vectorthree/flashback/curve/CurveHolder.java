package ml.mypals.vectorthree.flashback.curve;

import org.jetbrains.annotations.Nullable;

/** Implemented on Flashback's Keyframe by KeyframeCurveMixin. */
public interface CurveHolder {
    @Nullable SpeedCurve vector3$curve();

    void vector3$setCurve(@Nullable SpeedCurve curve);
}
