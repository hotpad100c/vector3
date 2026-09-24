package ml.mypals.vectorthree.shape.area;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AreaProjection {
    public record Projection(AABB source, Vec3 sourceCenter, Vec3 destCenter, Quaternionf rotation, Vec3 scale,
            boolean entities, boolean particles, String shapeId, float alpha) {
        public boolean translucent() {
            return alpha < 1.0f;
        }

        public Vec3 map(double x, double y, double z) {
            Vector3d offset = new Vector3d((x - sourceCenter.x) * scale.x, (y - sourceCenter.y) * scale.y,
                    (z - sourceCenter.z) * scale.z).rotate(new org.joml.Quaterniond(rotation));
            return new Vec3(destCenter.x + offset.x, destCenter.y + offset.y, destCenter.z + offset.z);
        }
    }

    private static final Map<String, Projection> ACTIVE = new ConcurrentHashMap<>();
    // Entities of translucent areas, submitted by vanilla's entity pass and drawn by their AreaShape.
    private static final Map<String, AreaTranslucentSubmitNodeStorage> TRANSLUCENT_ENTITIES = new HashMap<>();
    private static float particleAlpha = 1.0f;

    private AreaProjection() {}

    public static void beginEntitySubmits() {
        TRANSLUCENT_ENTITIES.clear();
    }

    public static SubmitNodeCollector translucentEntityCollector(Projection projection) {
        return TRANSLUCENT_ENTITIES.computeIfAbsent(projection.shapeId(), id -> new AreaTranslucentSubmitNodeStorage());
    }

    static boolean hasTranslucentEntities(String shapeId) {
        return TRANSLUCENT_ENTITIES.containsKey(shapeId);
    }

    static SubmitNodeStorage takeTranslucentEntities(String shapeId) {
        SubmitNodeStorage storage = TRANSLUCENT_ENTITIES.remove(shapeId);
        return storage != null ? storage : new AreaTranslucentSubmitNodeStorage();
    }

    public static float particleAlpha() {
        return particleAlpha;
    }

    public static void withParticleAlpha(float alpha, Runnable extract) {
        float previous = particleAlpha;
        particleAlpha = alpha;
        try {
            extract.run();
        } finally {
            particleAlpha = previous;
        }
    }

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
