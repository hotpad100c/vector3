package ml.mypals.vectorthree.shape.area;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AreaProjection {
    public record Projection(AABB source, Matrix4d transform, boolean entities, boolean particles, String shapeId,
            float alpha) {
        public boolean translucent() {
            return alpha < 1.0f;
        }

        public Vec3 map(double x, double y, double z) {
            Vector3d mapped = transform.transformPosition(new Vector3d(x, y, z));
            return new Vec3(mapped.x, mapped.y, mapped.z);
        }

        public Matrix4f linear() {
            return new Matrix4f(transform).setTranslation(0, 0, 0);
        }
    }

    private static final Map<String, Projection> ACTIVE = new ConcurrentHashMap<>();
    private static float particleAlpha = 1.0f;

    private AreaProjection() {}

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
