package ml.mypals.vectorthree.camera.orbit;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Flashback's orbit, extended with a tilted orbit plane. Flashback places the eye at
 * {@code center - forward(yaw, pitch) * distance}, so yaw circles a horizontal ring and pitch is the
 * elevation above it. The tilt rotates that whole frame (about X, then Z), so yaw circles a tilted ring
 * and pitch is the elevation relative to it; the camera still looks at the center and stays upright.
 */
public final class OrbitMath {
    private OrbitMath() {}

    public static Quaterniond tilt(double tiltX, double tiltZ) {
        return new Quaterniond().rotateX(Math.toRadians(tiltX)).rotateZ(Math.toRadians(tiltZ));
    }

    /** Center-to-eye offset in the untilted frame (the negated MC view vector times distance). */
    public static Vector3d flatOffset(double yaw, double pitch, double distance) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vector3d(Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p)).mul(distance);
    }

    public static Vector3d eyeOffset(double yaw, double pitch, double distance, double tiltX, double tiltZ) {
        return tilt(tiltX, tiltZ).transform(flatOffset(yaw, pitch, distance));
    }

    /** {yaw, pitch} looking from {@code center + offset} back at the center, yaw kept near {@code nearYaw}. */
    public static double[] lookAngles(Vector3d offset, double nearYaw) {
        Vector3d direction = new Vector3d(offset).negate().normalize();
        double pitch = -Math.toDegrees(Math.asin(Math.clamp(direction.y, -1, 1)));
        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        yaw += 360 * Math.round((nearYaw - yaw) / 360);
        return new double[]{yaw, pitch};
    }

    /** {yaw, pitch, distance} for a world-space center-to-eye offset, inverse of {@link #eyeOffset}. */
    public static double[] orbitOf(Vector3d offset, double tiltX, double tiltZ, double nearYaw) {
        Vector3d local = tilt(tiltX, tiltZ).conjugate().transform(new Vector3d(offset));
        double distance = local.length();
        if (distance < 1.0e-6) return new double[]{nearYaw, 0, 0};
        double pitch = Math.toDegrees(Math.asin(Math.clamp(local.y / distance, -1, 1)));
        double yaw = Math.toDegrees(Math.atan2(local.x, -local.z));
        yaw += 360 * Math.round((nearYaw - yaw) / 360);
        return new double[]{yaw, pitch, distance};
    }

    /** {tiltX, tiltZ} whose orbit-plane normal is {@code normal}; the in-plane twist is left to yaw. */
    public static double[] tiltOf(Vector3d normal) {
        Vector3d n = new Vector3d(normal).normalize();
        double tiltZ = Math.toDegrees(Math.asin(Math.clamp(-n.x, -1, 1)));
        double tiltX = Math.toDegrees(Math.atan2(n.z, n.y));
        return new double[]{tiltX, tiltZ};
    }
}
