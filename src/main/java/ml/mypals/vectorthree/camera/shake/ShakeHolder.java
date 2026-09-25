package ml.mypals.vectorthree.camera.shake;

import org.jetbrains.annotations.Nullable;

/** Implemented on CameraShakeKeyframe, KeyframeChangeCameraShake and ReplayVisuals by mixins. */
public interface ShakeHolder {
    @Nullable ShakeParams vector3$shake();

    void vector3$setShake(@Nullable ShakeParams params);

    /** Noise phases for yaw, pitch, roll and position, or null to derive them from constant frequencies. */
    default double @Nullable [] vector3$phases() {
        return null;
    }

    default void vector3$setPhases(double @Nullable [] phases) {}
}
