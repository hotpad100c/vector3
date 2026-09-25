package ml.mypals.vectorthree.camera.shake;

/** Flashback's own camera shake fields, exposed on CameraShakeKeyframe by CameraShakeKeyframeMixin. */
public interface ShakeKeyframe {
    float vector3$frequencyX();

    float vector3$frequencyY();

    void vector3$setBase(float frequencyX, float amplitudeX, float frequencyY, float amplitudeY);
}
