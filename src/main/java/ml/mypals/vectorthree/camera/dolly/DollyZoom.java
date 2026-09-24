package ml.mypals.vectorthree.camera.dolly;

import ml.mypals.vectorthree.camera.target.Target;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One Dolly Zoom keyframe. The camera sits {@code distance} from the target along the view direction
 * {@code yaw}/{@code pitch}, and its FOV keeps {@code frameHeight} blocks visible at the target's
 * distance, so the target keeps its size on screen while the distance changes.
 */
public record DollyZoom(boolean endsScope, Target target, float distance, float frameHeight, float yaw, float pitch) {
    public record Pose(Vec3 eye, float yaw, float pitch, float fov) {}

    public static float frameHeight(float distance, float fov) {
        return (float) (2 * distance * Math.tan(Math.toRadians(fov) / 2));
    }

    /** The camera between {@code from} and {@code to}, around an already resolved target point. */
    public static Pose pose(DollyZoom from, DollyZoom to, float amount, Vec3 target) {
        float distance = Mth.lerp(amount, from.distance, to.distance);
        float frame = Mth.lerp(amount, from.frameHeight, to.frameHeight);
        float yaw = from.yaw + Mth.wrapDegrees(to.yaw - from.yaw) * amount;
        float pitch = Mth.lerp(amount, from.pitch, to.pitch);
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vec3 forward = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        float fov = (float) Math.toDegrees(2 * Math.atan(frame / (2 * Math.max(0.01, distance))));
        return new Pose(target.subtract(forward.scale(distance)), yaw, pitch, Math.clamp(fov, 1, 170));
    }

    public DollyZoom withEndsScope(boolean endsScope) {
        return new DollyZoom(endsScope, target, distance, frameHeight, yaw, pitch);
    }

    public DollyZoom withTarget(Target target) {
        return new DollyZoom(endsScope, target, distance, frameHeight, yaw, pitch);
    }

    public DollyZoom withShot(float distance, float frameHeight, float yaw, float pitch) {
        return new DollyZoom(endsScope, target, distance, frameHeight, yaw, pitch);
    }
}
