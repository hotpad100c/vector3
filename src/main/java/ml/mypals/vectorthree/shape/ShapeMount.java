package ml.mypals.vectorthree.shape;

import com.moulberry.flashback.combo_options.TrackingBodyPart;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.UUID;


public record ShapeMount(UUID entity, TrackingBodyPart part, boolean followRotation) {
    public ShapeMount sanitized() {
        return part == null ? new ShapeMount(entity, TrackingBodyPart.ROOT, followRotation) : this;
    }

    public ShapeMount withPart(TrackingBodyPart part) {
        return new ShapeMount(entity, part, followRotation);
    }

    public ShapeMount withFollowRotation(boolean followRotation) {
        return new ShapeMount(entity, part, followRotation);
    }

    public @Nullable Entity resolve() {
        Minecraft minecraft = Minecraft.getInstance();
        return entity == null || minecraft.level == null ? null : minecraft.level.getEntity(entity);
    }

    public @Nullable Matrix4f transform() {
        Entity target = resolve();
        if (target == null) return null;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TrackingBodyPart bodyPart = part == null ? TrackingBodyPart.ROOT : part;
        Vec3 point = switch (bodyPart) {
            case HEAD -> target.getEyePosition(partialTick);
            case BODY -> target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
            case ROOT -> target.getPosition(partialTick);
        };
        Matrix4f transform = new Matrix4f().translation((float) point.x, (float) point.y, (float) point.z);
        if (!followRotation) return transform;
        LivingEntity living = target instanceof LivingEntity l ? l : null;
        float yaw = switch (bodyPart) {
            case HEAD -> living != null ? Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot) : target.getYHeadRot();
            case BODY -> living != null ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : target.getYRot(partialTick);
            case ROOT -> target.getYRot(partialTick);
        };
        transform.rotateY((float) Math.toRadians(-yaw));
        if (bodyPart == TrackingBodyPart.HEAD) transform.rotateX((float) Math.toRadians(target.getXRot(partialTick)));
        return transform;
    }
}
