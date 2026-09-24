package ml.mypals.vectorthree.prefab;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public record PrefabTransform(Vector3d center, Vector3f rotationDegrees, double scale) {
    public static final PrefabTransform IDENTITY = new PrefabTransform(new Vector3d(), new Vector3f(), 1);

    public Quaterniond rotation() {
        return new Quaterniond().rotateXYZ(Math.toRadians(rotationDegrees.x), Math.toRadians(rotationDegrees.y),
                Math.toRadians(rotationDegrees.z));
    }

    public Vector3d point(Vector3d local) {
        return rotation().transform(new Vector3d(local).mul(scale)).add(center);
    }

    public Vector3d direction(Vector3d local) {
        return rotation().transform(new Vector3d(local));
    }

    public float[] view(float yaw, float pitch, float roll) {
        Quaterniond rotated = rotation().mul(viewRotation(yaw, pitch, roll));
        Vector3d forward = rotated.transform(new Vector3d(0, 0, -1));
        double newYaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
        double newPitch = Math.toDegrees(-Math.asin(Math.clamp(forward.y, -1, 1)));
        Quaterniond twist = viewRotation((float) newYaw, (float) newPitch, 0).conjugate().mul(rotated);
        double newRoll = Math.toDegrees(2 * Math.atan2(twist.z, twist.w));
        return new float[]{(float) nearAngle(newYaw, yaw), (float) newPitch, (float) nearAngle(newRoll, roll)};
    }

    public PrefabTransform inverse() {
        Quaterniond inverse = rotation().conjugate();
        Vector3d euler = inverse.getEulerAnglesXYZ(new Vector3d());
        return new PrefabTransform(inverse.transform(new Vector3d(center).negate()).div(scale),
                new Vector3f((float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z)),
                1 / scale);
    }

    static Quaterniond viewRotation(float yaw, float pitch, float roll) {
        return new Quaterniond().rotationYXZ(Math.PI - Math.toRadians(yaw), -Math.toRadians(pitch), 0)
                .rotateZ(Math.toRadians(roll));
    }

    public static float[] lookAt(Vector3d eye, Vector3d target) {
        Vector3d d = new Vector3d(target).sub(eye);
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        return new float[]{(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) Math.toDegrees(-Math.atan2(d.y, horizontal))};
    }

    private static double nearAngle(double angle, double reference) {
        return reference + ((angle - reference) % 360 + 540) % 360 - 180;
    }
}
