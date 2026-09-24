package ml.mypals.vectorthree.camera.orbit;

/** Orbit-plane tilt added to Flashback's CameraOrbitKeyframe and its KeyframeChange by mixin. */
public interface OrbitTilt {
    float vector3$tiltX();

    float vector3$tiltZ();

    void vector3$setTilt(float tiltX, float tiltZ);
}
