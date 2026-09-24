package ml.mypals.vectorthree.shape.area;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AreaProjection {
    public record Projection(AABB source, Vec3 sourceCenter, Vec3 destCenter, Quaternionf rotation, Vec3 scale,
            boolean entities, boolean particles) {
        public Vec3 map(double x, double y, double z) {
            Vector3d offset = new Vector3d((x - sourceCenter.x) * scale.x, (y - sourceCenter.y) * scale.y,
                    (z - sourceCenter.z) * scale.z).rotate(new org.joml.Quaterniond(rotation));
            return new Vec3(destCenter.x + offset.x, destCenter.y + offset.y, destCenter.z + offset.z);
        }
    }

    private static final Map<String, Projection> ACTIVE = new ConcurrentHashMap<>();

    private AreaProjection() {}

    static void set(String shapeId, Projection projection) {
        if (projection == null || !(projection.entities() || projection.particles())) ACTIVE.remove(shapeId);
        else ACTIVE.put(shapeId, projection);
    }

    static void clear(String shapeId) {
        ACTIVE.remove(shapeId);
    }

    static Collection<Projection> all() {
        return ACTIVE.values();
    }

    public static Projection forEntity(Entity entity) {
        if (ACTIVE.isEmpty() || isViewer(entity)) return null;
        for (Projection projection : ACTIVE.values()) {
            if (projection.entities() && projection.source().contains(entity.position())) return projection;
        }
        return null;
    }

    public static Projection forParticle(double x, double y, double z) {
        if (ACTIVE.isEmpty()) return null;
        for (Projection projection : ACTIVE.values()) {
            if (projection.particles() && projection.source().contains(x, y, z)) return projection;
        }
        return null;
    }

    static boolean isViewer(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity == minecraft.getCameraEntity() || entity == minecraft.player;
    }
}
