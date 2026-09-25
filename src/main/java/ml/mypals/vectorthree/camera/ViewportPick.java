package ml.mypals.vectorthree.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class ViewportPick {
    public static final double RANGE = 256;

    public record Hit(Vec3 location, @Nullable BlockHitResult block, @Nullable Entity entity) {}

    private ViewportPick() {}

    public static @Nullable Hit pick(Vec3 eye, Vec3 direction) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        Entity viewer = minecraft.getCameraEntity();
        if (level == null || viewer == null) return null;
        Vec3 end = eye.add(direction.normalize().scale(RANGE));

        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        double best = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : eye.distanceToSqr(block.getLocation());
        Hit hit = block.getType() == HitResult.Type.MISS ? null : new Hit(block.getLocation(), block, null);

        AABB sweep = new AABB(eye, end).inflate(1);
        for (Entity entity : level.getEntities(viewer, sweep, entity -> entity != minecraft.player && !entity.isSpectator())) {
            Optional<Vec3> point = entity.getBoundingBox().inflate(entity.getPickRadius()).clip(eye, end);
            if (point.isEmpty()) continue;
            double distance = eye.distanceToSqr(point.get());
            if (distance < best) {
                best = distance;
                hit = new Hit(point.get(), null, entity);
            }
        }
        return hit;
    }
}
