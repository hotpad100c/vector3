package ml.mypals.vectorthree.mc.shape;

import ml.mypals.vectorthree.mc.pose.EntityParts;
import ml.mypals.vectorthree.core.shape.ShapeMount;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ShapeMounts {
    private ShapeMounts() {}

    public static @Nullable Entity resolve(ShapeMount mount) {
        Minecraft minecraft = Minecraft.getInstance();
        return mount.entity() == null || minecraft.level == null ? null : minecraft.level.getEntity(mount.entity());
    }

    /** The mount point's transform in the world, or null when the entity isn't loaded. */
    public static @Nullable Matrix4f transform(ShapeMount mount) {
        Entity target = resolve(mount);
        if (target == null) return null;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (mount.modelPart() != null) {
            Matrix4f onPart = EntityParts.transform(target, mount.modelPart(), partialTick);
            if (onPart != null) {
                if (mount.followRotation()) return onPart;
                Vector3f pivot = onPart.getTranslation(new Vector3f());
                return new Matrix4f().translation(pivot);
            }
        }
        var bodyPart = mount.part() == null ? ml.mypals.vectorthree.core.entity.BodyPart.ROOT : mount.part();
        Vec3 point = switch (bodyPart) {
            case HEAD -> target.getEyePosition(partialTick);
            case BODY -> target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
            case ROOT -> target.getPosition(partialTick);
        };
        Matrix4f transform = new Matrix4f().translation((float) point.x, (float) point.y, (float) point.z);
        if (!mount.followRotation()) return transform;
        LivingEntity living = target instanceof LivingEntity l ? l : null;
        float yaw = switch (bodyPart) {
            case HEAD -> living != null ? Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot) : target.getYHeadRot();
            case BODY -> living != null ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : target.getYRot(partialTick);
            case ROOT -> target.getYRot(partialTick);
        };
        transform.rotateY((float) Math.toRadians(-yaw));
        if (bodyPart == ml.mypals.vectorthree.core.entity.BodyPart.HEAD) transform.rotateX((float) Math.toRadians(target.getXRot(partialTick)));
        return transform;
    }
}
