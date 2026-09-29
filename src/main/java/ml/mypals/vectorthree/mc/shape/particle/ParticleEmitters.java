package ml.mypals.vectorthree.mc.shape.particle;

import ml.mypals.vectorthree.core.shape.particle.ParticleSettings;

import ml.mypals.vectorthree.core.port.Ports;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.vectorthree.mixin.minecraft.area.ParticleAccessor;
import ml.mypals.vectorthree.mixin.minecraft.particle.QuadParticleAccessor;
import ml.mypals.vectorthree.core.shape.ShapeState;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the particle emitter shapes from replay time, so they emit steadily whenever the replay advances (playing
 * or exporting) and not at all while it is paused or being scrubbed. The shape itself is only the editor gizmo.
 */
public final class ParticleEmitters {
    public static final Color GIZMO_COLOR = new Color(120, 200, 255, 220);
    private static final int GIZMO_SEGMENTS = 32;
    // Replay jumps larger than this are scrubbing, not playback: nothing is emitted for them.
    private static final double MAX_STEP_TICKS = 20;
    private static final int MAX_PER_FRAME = 500;

    private record Live(Particle particle, ParticleSettings settings, float quadSize, float r, float g, float b, float a) {}

    private static final class Emitter {
        double tick = Double.NaN;
        double carry;
        Vec3 pivot;
        final List<Live> live = new ArrayList<>();
    }

    private static final Map<String, Emitter> EMITTERS = new HashMap<>();

    private ParticleEmitters() {}

    public static void clear() {
        EMITTERS.clear();
    }

    public static void retainOnly(Set<String> shapeIds) {
        EMITTERS.keySet().retainAll(shapeIds);
    }

    /** Once per rendered frame. */
    public static void frame() {
        Minecraft minecraft = Minecraft.getInstance();
        var clock = Ports.clock();
        if (minecraft.level == null || !clock.replayLoaded()) {
            EMITTERS.clear();
            return;
        }
        double tick = clock.exporting() ? clock.exportTick() : clock.partialTick();
        for (String shapeId : ShapeTrackRegistry.shapeIds()) {
            if (!"particle".equals(ShapeTrackRegistry.typeOf(shapeId))) continue;
            ShapeState state = ShapeTrackRegistry.state(shapeId);
            Shape shape = ShapeTrackRegistry.shape(shapeId);
            if (state == null || shape == null) continue;
            update(minecraft, shapeId, EMITTERS.computeIfAbsent(shapeId, id -> new Emitter()), state, shape, tick);
        }
    }

    private static void update(Minecraft minecraft, String shapeId, Emitter emitter, ShapeState state, Shape shape, double tick) {
        ParticleSettings settings = ParticleSettings.orDefault(state.particle());
        Vec3 pivot = shape.transformer.getShapeWorldPivot(false);
        Quaternionf rotation = new Quaternionf(shape.transformer.getShapeWorldRotation(false));
        Vec3 scale = shape.transformer.getShapeWorldScale(false);
        double previous = emitter.tick;
        Vec3 lastPivot = emitter.pivot;
        emitter.tick = tick;
        emitter.pivot = pivot;
        emitter.live.removeIf(live -> !live.particle().isAlive());
        if (settings.localSpace() && lastPivot != null) follow(emitter, pivot.subtract(lastPivot));

        double elapsed = tick - previous;
        boolean active = state.visible() && !ShapeTrackRegistry.hiddenByMount(shapeId);
        if (elapsed == 0) return;
        if (!active || Double.isNaN(previous) || elapsed < 0 || elapsed > MAX_STEP_TICKS) {
            emitter.carry = 0;
            return;
        }
        Object registered = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.tryParse(state.model()));
        if (!(registered instanceof ParticleOptions options)) return;

        double amount = emitter.carry + elapsed / 20.0 * Math.max(0, state.lineWidth())
                + (lastPivot == null ? 0 : pivot.distanceTo(lastPivot) * Math.max(0, settings.rateOverDistance()));
        int count = (int) amount;
        emitter.carry = amount - count;
        count = Math.min(MAX_PER_FRAME, count + bursts(settings, previous, tick));
        if (settings.maxParticles() > 0) count = Math.min(count, settings.maxParticles() - emitter.live.size());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector3f position = new Vector3f(), direction = new Vector3f();
            sample(settings, state, random, position, direction);
            position.mul((float) scale.x, (float) scale.y, (float) scale.z).rotate(rotation);
            direction.rotate(rotation).normalize();
            double speed = between(random, settings.speedMin(), settings.speedMax()) / 20.0;
            spawn(minecraft, emitter, options, settings, state, pivot.add(position.x, position.y, position.z),
                    direction.x * speed, direction.y * speed, direction.z * speed, random);
        }
    }

    private static void spawn(Minecraft minecraft, Emitter emitter, ParticleOptions options, ParticleSettings settings,
            ShapeState state, Vec3 at, double vx, double vy, double vz, ThreadLocalRandom random) {
        Particle particle = minecraft.particleEngine.createParticle(options, at.x, at.y, at.z, vx, vy, vz);
        if (particle == null) return;
        if (!settings.nativeMotion()) particle.setParticleSpeed(vx, vy, vz);
        if (settings.lifetimeMax() > 0) {
            particle.setLifetime(Math.max(1, (int) Math.round(between(random, settings.lifetimeMin(), settings.lifetimeMax()) * 20)));
        }
        float size = (float) between(random, settings.sizeMin(), settings.sizeMax());
        if (size != 1 && size > 0) particle.scale(size);
        if (settings.physics()) {
            ParticleAccessor accessor = (ParticleAccessor) particle;
            accessor.vector3$setGravity(settings.gravity());
            accessor.vector3$setFriction((float) Math.clamp(1 - settings.drag() / 20.0, 0, 1));
            accessor.vector3$setHasPhysics(settings.collision());
        }
        float quadSize = 0, r = 1, g = 1, b = 1, a = 1;
        if (particle instanceof SingleQuadParticle quad) {
            QuadParticleAccessor accessor = (QuadParticleAccessor) quad;
            if (settings.tint()) {
                int color = state.color();
                quad.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f);
                accessor.vector3$setAlpha(((color >>> 24) & 255) / 255f);
            }
            quadSize = accessor.vector3$quadSize();
            r = accessor.vector3$rCol();
            g = accessor.vector3$gCol();
            b = accessor.vector3$bCol();
            a = accessor.vector3$alpha();
        }
        emitter.live.add(new Live(particle, settings, quadSize, r, g, b, a));
    }

    /** After ParticleEngine#tick: force, noise and the over-lifetime modules. */
    public static void tick() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Emitter emitter : EMITTERS.values()) {
            emitter.live.removeIf(live -> !live.particle().isAlive());
            for (Live live : emitter.live) {
                ParticleSettings settings = live.settings();
                ParticleAccessor particle = (ParticleAccessor) live.particle();
                double noise = settings.noise() / 400.0;
                if (settings.forceX() != 0 || settings.forceY() != 0 || settings.forceZ() != 0 || noise > 0) {
                    particle.vector3$setXd(particle.vector3$xd() + settings.forceX() / 400.0 + (random.nextDouble() - 0.5) * 2 * noise);
                    particle.vector3$setYd(particle.vector3$yd() + settings.forceY() / 400.0 + (random.nextDouble() - 0.5) * 2 * noise);
                    particle.vector3$setZd(particle.vector3$zd() + settings.forceZ() / 400.0 + (random.nextDouble() - 0.5) * 2 * noise);
                }
                if (!(live.particle() instanceof SingleQuadParticle quad)) continue;
                float t = Math.clamp(particle.vector3$age() / (float) Math.max(1, live.particle().getLifetime()), 0f, 1f);
                QuadParticleAccessor accessor = (QuadParticleAccessor) quad;
                if (settings.colorOverLifetime()) {
                    int end = settings.endColor();
                    quad.setColor(mix(live.r(), ((end >> 16) & 255) / 255f, t), mix(live.g(), ((end >> 8) & 255) / 255f, t),
                            mix(live.b(), (end & 255) / 255f, t));
                    accessor.vector3$setAlpha(mix(live.a(), ((end >>> 24) & 255) / 255f, t));
                }
                if (settings.endSize() != 1) accessor.vector3$setQuadSize(live.quadSize() * mix(1, settings.endSize(), t));
            }
        }
    }

    // Local simulation space: the particles ride along with the emitter's position.
    private static void follow(Emitter emitter, Vec3 delta) {
        if (delta.lengthSqr() < 1.0e-12) return;
        for (Live live : emitter.live) {
            Particle particle = live.particle();
            ParticleAccessor accessor = (ParticleAccessor) particle;
            particle.setPos(accessor.vector3$x() + delta.x, accessor.vector3$y() + delta.y, accessor.vector3$z() + delta.z);
            accessor.vector3$setXo(accessor.vector3$xo() + delta.x);
            accessor.vector3$setYo(accessor.vector3$yo() + delta.y);
            accessor.vector3$setZo(accessor.vector3$zo() + delta.z);
        }
    }

    /** The bursts at burstTick + k * burstInterval that fall in (from, to]. */
    static int bursts(ParticleSettings settings, double from, double to) {
        if (settings.burstCount() <= 0) return 0;
        int interval = Math.max(1, settings.burstInterval());
        long first = Math.max(0, (long) Math.floor((from - settings.burstTick()) / interval) + 1);
        long last = (long) Math.floor((to - settings.burstTick()) / interval);
        if (settings.burstCycles() > 0) last = Math.min(last, settings.burstCycles() - 1L);
        return last < first ? 0 : (int) Math.min(MAX_PER_FRAME, (last - first + 1) * settings.burstCount());
    }

    /** A spawn point and direction in the emitter's local space (before its scale and rotation). */
    static void sample(ParticleSettings settings, ShapeState state, ThreadLocalRandom random, Vector3f position, Vector3f direction) {
        float radius = Math.max(0, settings.radius());
        switch (settings.shape()) {
            case SPHERE, HEMISPHERE -> {
                randomUnit(random, direction);
                if (settings.shape() == ParticleSettings.Kind.HEMISPHERE) direction.y = Math.abs(direction.y);
                float inner = 1 - Math.clamp(settings.thickness(), 0, 1);
                float distance = radius * (float) Math.cbrt(inner * inner * inner + random.nextDouble() * (1 - inner * inner * inner));
                position.set(direction).mul(distance);
            }
            case CONE, CIRCLE -> {
                double theta = Math.toRadians(random.nextDouble() * Math.clamp(settings.arc(), 0, 360));
                float inner = 1 - Math.clamp(settings.thickness(), 0, 1);
                double fraction = Math.sqrt(inner * inner + random.nextDouble() * (1 - inner * inner));
                float cos = (float) Math.cos(theta), sin = (float) Math.sin(theta);
                position.set(cos * radius * fraction, 0, sin * radius * fraction);
                if (settings.shape() == ParticleSettings.Kind.CIRCLE) {
                    direction.set(cos, 0, sin);
                } else {
                    double tilt = Math.toRadians(Math.clamp(settings.angle(), 0, 90) * fraction);
                    direction.set(cos * (float) Math.sin(tilt), (float) Math.cos(tilt), sin * (float) Math.sin(tilt));
                }
            }
            case BOX -> {
                position.set((random.nextFloat() - 0.5f) * state.sizeX(), (random.nextFloat() - 0.5f) * state.sizeY(),
                        (random.nextFloat() - 0.5f) * state.sizeZ());
                direction.set(0, 1, 0);
            }
            case EDGE -> {
                position.set((random.nextFloat() * 2 - 1) * radius, 0, 0);
                direction.set(0, 1, 0);
            }
        }
        float randomize = Math.clamp(settings.randomDirection(), 0, 1);
        if (randomize > 0) {
            Vector3f other = randomUnit(random, new Vector3f());
            direction.lerp(other, randomize);
            if (direction.lengthSquared() < 1.0e-8f) direction.set(other);
        }
        direction.normalize();
    }

    /** The emitter's outline in local space, as one continuous strip (some edges are drawn twice). */
    public static List<Vec3> gizmoPath(ShapeState state) {
        ParticleSettings settings = ParticleSettings.orDefault(state.particle());
        double r = Math.max(0.01, settings.radius());
        double arc = Math.clamp(settings.arc(), 1, 360);
        List<Vec3> path = new ArrayList<>();
        switch (settings.shape()) {
            case SPHERE -> {
                ring(path, r, 0, 360, (c, s) -> new Vec3(c, 0, s));
                ring(path, r, 0, 360, (c, s) -> new Vec3(c, s, 0));
                ring(path, r, 0, 90, (c, s) -> new Vec3(c, s, 0));
                ring(path, r, 90, 450, (c, s) -> new Vec3(0, s, c));
            }
            case HEMISPHERE -> {
                ring(path, r, 0, 360, (c, s) -> new Vec3(c, 0, s));
                ring(path, r, 0, 180, (c, s) -> new Vec3(c, s, 0));
                ring(path, r, 180, 270, (c, s) -> new Vec3(c, 0, s));
                ring(path, r, -90, 90, (c, s) -> new Vec3(0, c, s));
            }
            case CONE -> {
                double length = Math.max(0.5, r * 2);
                double top = r + length * Math.tan(Math.toRadians(Math.clamp(settings.angle(), 0, 89)));
                path.add(new Vec3(0, length, 0));
                path.add(Vec3.ZERO);
                for (int quarter = 0; quarter * 90 <= arc; quarter++) {
                    double angle = Math.min(quarter * 90, arc);
                    if (quarter > 0) ring(path, r, (quarter - 1) * 90, angle, (c, s) -> new Vec3(c, 0, s));
                    else path.add(new Vec3(r, 0, 0));
                    Vec3 base = path.getLast();
                    double cos = Math.cos(Math.toRadians(angle)), sin = Math.sin(Math.toRadians(angle));
                    path.add(new Vec3(cos * top, length, sin * top));
                    path.add(base);
                }
                double done = Math.floor(arc / 90) * 90;
                if (done < arc) ring(path, r, done, arc, (c, s) -> new Vec3(c, 0, s));
                double cos = Math.cos(Math.toRadians(arc)), sin = Math.sin(Math.toRadians(arc));
                path.add(new Vec3(cos * top, length, sin * top));
                ring(path, top, arc, 0, (c, s) -> new Vec3(c, length, s));
            }
            case BOX -> {
                double x = state.sizeX() / 2, y = state.sizeY() / 2, z = state.sizeZ() / 2;
                Vec3 b0 = new Vec3(-x, -y, -z), b1 = new Vec3(x, -y, -z), b2 = new Vec3(x, -y, z), b3 = new Vec3(-x, -y, z);
                Vec3 t0 = new Vec3(-x, y, -z), t1 = new Vec3(x, y, -z), t2 = new Vec3(x, y, z), t3 = new Vec3(-x, y, z);
                path.addAll(List.of(b0, b1, b2, b3, b0, t0, t1, b1, t1, t2, b2, t2, t3, b3, t3, t0));
            }
            case CIRCLE -> {
                path.add(Vec3.ZERO);
                ring(path, r, 0, arc, (c, s) -> new Vec3(c, 0, s));
                if (arc < 360) path.add(Vec3.ZERO);
            }
            case EDGE -> path.addAll(List.of(new Vec3(-r, 0, 0), Vec3.ZERO, new Vec3(0, Math.max(0.25, r * 0.5), 0),
                    Vec3.ZERO, new Vec3(r, 0, 0)));
        }
        return path;
    }

    private interface Plane { Vec3 at(double cos, double sin); }

    private static void ring(List<Vec3> path, double radius, double fromDegrees, double toDegrees, Plane plane) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(toDegrees - fromDegrees) / 360 * GIZMO_SEGMENTS));
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * i / steps);
            path.add(plane.at(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
    }

    private static Vector3f randomUnit(ThreadLocalRandom random, Vector3f into) {
        double z = random.nextDouble() * 2 - 1, phi = random.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return into.set((float) (s * Math.cos(phi)), (float) z, (float) (s * Math.sin(phi)));
    }

    private static double between(ThreadLocalRandom random, float min, float max) {
        return min >= max ? min : min + random.nextDouble() * (max - min);
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
