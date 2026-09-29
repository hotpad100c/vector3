package ml.mypals.vectorthree.core.shape.particle;

/**
 * A particle emitter's modules, after Unity's Particle System. Times are seconds, speeds blocks/s, forces blocks/s²;
 * the emission rate, box size and start color stay on ShapeState (lineWidth, size, color).
 */
public record ParticleSettings(
        Kind shape, float radius, float thickness, float angle, float arc, float randomDirection,
        float lifetimeMin, float lifetimeMax, float speedMin, float speedMax, float sizeMin, float sizeMax,
        boolean nativeMotion, boolean tint, int maxParticles, boolean localSpace,
        float rateOverDistance, int burstTick, int burstCount, int burstInterval, int burstCycles,
        float forceX, float forceY, float forceZ, float noise,
        boolean colorOverLifetime, int endColor, float endSize,
        boolean physics, float gravity, float drag, boolean collision) {

    /** Every shape emits along its local +Y, except the sphere, hemisphere and circle, which emit outward. */
    public enum Kind { CONE, SPHERE, HEMISPHERE, BOX, CIRCLE, EDGE }

    public static final ParticleSettings DEFAULT = new ParticleSettings(Kind.CONE, 0.25f, 1, 25, 360, 0,
            0, 0, 2, 2, 1, 1, false, false, 0, false,
            0, 0, 0, 20, 1, 0, 0, 0, 0,
            false, 0x00FFFFFF, 1, false, 1, 0, true);

    /** Emitters saved before the modules existed: a box of random points, with the particle type's own motion. */
    public static final ParticleSettings LEGACY = new ParticleSettings(Kind.BOX, 0.25f, 1, 25, 360, 0,
            0, 0, 0, 0, 1, 1, true, false, 0, false,
            0, 0, 0, 20, 1, 0, 0, 0, 0,
            false, 0x00FFFFFF, 1, false, 1, 0, true);

    public ParticleSettings {
        if (shape == null) shape = Kind.CONE;
    }

    public static ParticleSettings orDefault(ParticleSettings settings) {
        return settings == null ? LEGACY : settings;
    }

    public static ParticleSettings transition(ParticleSettings from, ParticleSettings to, double amount) {
        if (from == null && to == null) return null;
        ParticleSettings a = orDefault(from), b = orDefault(to);
        boolean first = amount < 0.5;
        ParticleSettings d = first ? a : b;
        return new ParticleSettings(d.shape, lerp(a.radius, b.radius, amount), lerp(a.thickness, b.thickness, amount),
                lerp(a.angle, b.angle, amount), lerp(a.arc, b.arc, amount), lerp(a.randomDirection, b.randomDirection, amount),
                lerp(a.lifetimeMin, b.lifetimeMin, amount), lerp(a.lifetimeMax, b.lifetimeMax, amount),
                lerp(a.speedMin, b.speedMin, amount), lerp(a.speedMax, b.speedMax, amount),
                lerp(a.sizeMin, b.sizeMin, amount), lerp(a.sizeMax, b.sizeMax, amount),
                d.nativeMotion, d.tint, d.maxParticles, d.localSpace,
                lerp(a.rateOverDistance, b.rateOverDistance, amount),
                d.burstTick, d.burstCount, d.burstInterval, d.burstCycles,
                lerp(a.forceX, b.forceX, amount), lerp(a.forceY, b.forceY, amount), lerp(a.forceZ, b.forceZ, amount),
                lerp(a.noise, b.noise, amount),
                d.colorOverLifetime, lerpColor(a.endColor, b.endColor, amount), lerp(a.endSize, b.endSize, amount),
                d.physics, lerp(a.gravity, b.gravity, amount), lerp(a.drag, b.drag, amount), d.collision);
    }

    private static float lerp(float from, float to, double amount) {
        return (float) (from + (to - from) * amount);
    }

    private static int lerpColor(int from, int to, double amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int channel = (int) Math.round(lerp((from >>> shift) & 255, (to >>> shift) & 255, amount));
            result |= Math.clamp(channel, 0, 255) << shift;
        }
        return result;
    }

    public ParticleSettings withShape(Kind shape, float radius, float thickness, float angle, float arc, float randomDirection) {
        return new ParticleSettings(shape, radius, thickness, angle, arc, randomDirection, lifetimeMin, lifetimeMax,
                speedMin, speedMax, sizeMin, sizeMax, nativeMotion, tint, maxParticles, localSpace, rateOverDistance,
                burstTick, burstCount, burstInterval, burstCycles, forceX, forceY, forceZ, noise, colorOverLifetime,
                endColor, endSize, physics, gravity, drag, collision);
    }

    public ParticleSettings withMain(float lifetimeMin, float lifetimeMax, float speedMin, float speedMax, float sizeMin,
            float sizeMax, boolean nativeMotion, boolean tint, int maxParticles, boolean localSpace) {
        return new ParticleSettings(shape, radius, thickness, angle, arc, randomDirection, lifetimeMin, lifetimeMax,
                speedMin, speedMax, sizeMin, sizeMax, nativeMotion, tint, maxParticles, localSpace, rateOverDistance,
                burstTick, burstCount, burstInterval, burstCycles, forceX, forceY, forceZ, noise, colorOverLifetime,
                endColor, endSize, physics, gravity, drag, collision);
    }

    public ParticleSettings withEmission(float rateOverDistance, int burstTick, int burstCount, int burstInterval, int burstCycles) {
        return new ParticleSettings(shape, radius, thickness, angle, arc, randomDirection, lifetimeMin, lifetimeMax,
                speedMin, speedMax, sizeMin, sizeMax, nativeMotion, tint, maxParticles, localSpace, rateOverDistance,
                burstTick, burstCount, burstInterval, burstCycles, forceX, forceY, forceZ, noise, colorOverLifetime,
                endColor, endSize, physics, gravity, drag, collision);
    }

    public ParticleSettings withForce(float forceX, float forceY, float forceZ, float noise) {
        return new ParticleSettings(shape, radius, thickness, angle, arc, randomDirection, lifetimeMin, lifetimeMax,
                speedMin, speedMax, sizeMin, sizeMax, nativeMotion, tint, maxParticles, localSpace, rateOverDistance,
                burstTick, burstCount, burstInterval, burstCycles, forceX, forceY, forceZ, noise, colorOverLifetime,
                endColor, endSize, physics, gravity, drag, collision);
    }

    public ParticleSettings withLifetime(boolean colorOverLifetime, int endColor, float endSize) {
        return new ParticleSettings(shape, radius, thickness, angle, arc, randomDirection, lifetimeMin, lifetimeMax,
                speedMin, speedMax, sizeMin, sizeMax, nativeMotion, tint, maxParticles, localSpace, rateOverDistance,
                burstTick, burstCount, burstInterval, burstCycles, forceX, forceY, forceZ, noise, colorOverLifetime,
                endColor, endSize, physics, gravity, drag, collision);
    }

    public ParticleSettings withPhysics(boolean physics, float gravity, float drag, boolean collision) {
        return new ParticleSettings(shape, radius, thickness, angle, arc, randomDirection, lifetimeMin, lifetimeMax,
                speedMin, speedMax, sizeMin, sizeMax, nativeMotion, tint, maxParticles, localSpace, rateOverDistance,
                burstTick, burstCount, burstInterval, burstCycles, forceX, forceY, forceZ, noise, colorOverLifetime,
                endColor, endSize, physics, gravity, drag, collision);
    }
}
