package ml.mypals.vectorthree.shape.blast;

import ml.mypals.vectorthree.flashback.curve.SpeedCurve;

/**
 * A blast shape's motion. Its positions (path start, origin, sweep end, scatter center, path points) are the
 * shape's points, in its local space; times in ticks; spin in degrees; clump sizes in blocks.
 */
public record BlastSettings(
        Mode mode, Driver driver, int startTick, int duration, float progress,
        Trajectory trajectory, float arcHeight, float burst,
        float scatterRadius, float flatten, int seed,
        float stagger, Order order, Easing easing,
        float spin, float clumpStart, float clumpEnd, float alphaStart, float alphaEnd, float scaleStart, float scaleEnd,
        SweepShape sweepShape, float sweepRadius,
        SpeedCurve motionCurve, SpeedCurve spinCurve, SpeedCurve alphaCurve, SpeedCurve scaleCurve, SpeedCurve clumpCurve,
        ClumpShape clumpShape, float clumpJitter) {

    /** How blocks are grouped into clumps: nested boxes, boxes with noisy borders, or nested Voronoi cells. */
    public enum ClumpShape { GRID, NOISE, VORONOI }

    /** Which of the per-property curves; a property without its own curve follows the motion curve. */
    public enum Curve { MOTION, SPIN, ALPHA, SCALE, CLUMP }

    public enum Mode { DISPERSE, GATHER }
    public enum Driver { AUTO, MANUAL }
    public enum Trajectory { LINE, ARC, EXPLODE, PATH }
    public enum Order { RANDOM, FROM_ORIGIN, SWEEP, BOTTOM_UP, TOP_DOWN }
    public enum SweepShape { SPHERE, CUBE }
    public enum Easing { LINEAR, IN, OUT, IN_OUT }

    public static final BlastSettings DEFAULT = new BlastSettings(Mode.DISPERSE, Driver.AUTO, 0, 40, 0,
            Trajectory.EXPLODE, 3, 2, 8, 0.3f, 1,
            0.3f, Order.FROM_ORIGIN, Easing.OUT,
            360, 4, 1, 1, 1, 1, 1, SweepShape.SPHERE, 2, null, null, null, null, null, ClumpShape.GRID, 0.6f);

    public BlastSettings {
        if (mode == null) mode = Mode.DISPERSE;
        if (driver == null) driver = Driver.AUTO;
        if (trajectory == null) trajectory = Trajectory.EXPLODE;
        if (order == null) order = Order.FROM_ORIGIN;
        if (easing == null) easing = Easing.OUT;
        if (sweepShape == null) sweepShape = SweepShape.SPHERE;
        if (clumpShape == null) clumpShape = ClumpShape.GRID;
    }

    /** The motion curve; without one, the easing preset. */
    public SpeedCurve motion() {
        if (motionCurve != null) return motionCurve;
        return SpeedCurve.preset(switch (easing) {
            case LINEAR -> SpeedCurve.Preset.LINEAR;
            case IN -> SpeedCurve.Preset.EASE_IN;
            case OUT -> SpeedCurve.Preset.EASE_OUT;
            case IN_OUT -> SpeedCurve.Preset.EASE_IN_OUT;
        });
    }

    public SpeedCurve curve(Curve which) {
        SpeedCurve own = switch (which) {
            case MOTION -> null;
            case SPIN -> spinCurve;
            case ALPHA -> alphaCurve;
            case SCALE -> scaleCurve;
            case CLUMP -> clumpCurve;
        };
        return own != null ? own : motion();
    }

    public static BlastSettings orDefault(BlastSettings settings) {
        return settings == null ? DEFAULT : settings;
    }

    public static BlastSettings transition(BlastSettings from, BlastSettings to, double amount) {
        if (from == null && to == null) return null;
        BlastSettings a = orDefault(from), b = orDefault(to);
        BlastSettings d = amount < 0.5 ? a : b;
        return new BlastSettings(d.mode, d.driver, d.startTick, d.duration, lerp(a.progress, b.progress, amount),
                d.trajectory, lerp(a.arcHeight, b.arcHeight, amount), lerp(a.burst, b.burst, amount),
                lerp(a.scatterRadius, b.scatterRadius, amount), lerp(a.flatten, b.flatten, amount), d.seed,
                lerp(a.stagger, b.stagger, amount), d.order, d.easing,
                lerp(a.spin, b.spin, amount), lerp(a.clumpStart, b.clumpStart, amount), lerp(a.clumpEnd, b.clumpEnd, amount),
                lerp(a.alphaStart, b.alphaStart, amount), lerp(a.alphaEnd, b.alphaEnd, amount),
                lerp(a.scaleStart, b.scaleStart, amount), lerp(a.scaleEnd, b.scaleEnd, amount),
                d.sweepShape, lerp(a.sweepRadius, b.sweepRadius, amount),
                d.motionCurve, d.spinCurve, d.alphaCurve, d.scaleCurve, d.clumpCurve, d.clumpShape, d.clumpJitter);
    }

    private static float lerp(float from, float to, double amount) {
        return (float) (from + (to - from) * amount);
    }

    public Mutable mutable() {
        Mutable m = new Mutable();
        m.mode = mode; m.driver = driver; m.startTick = startTick; m.duration = duration; m.progress = progress;
        m.trajectory = trajectory; m.arcHeight = arcHeight; m.burst = burst;
        m.scatterRadius = scatterRadius;
        m.flatten = flatten; m.seed = seed; m.stagger = stagger; m.order = order; m.easing = easing;
        m.spin = spin; m.clumpStart = clumpStart; m.clumpEnd = clumpEnd; m.alphaStart = alphaStart; m.alphaEnd = alphaEnd;
        m.scaleStart = scaleStart; m.scaleEnd = scaleEnd;
        m.sweepShape = sweepShape; m.sweepRadius = sweepRadius;
        m.motionCurve = motionCurve; m.spinCurve = spinCurve; m.alphaCurve = alphaCurve; m.scaleCurve = scaleCurve;
        m.clumpCurve = clumpCurve;
        m.clumpShape = clumpShape; m.clumpJitter = clumpJitter;
        return m;
    }

    public static final class Mutable {
        public Mode mode;
        public Driver driver;
        public int startTick, duration;
        public float progress;
        public Trajectory trajectory;
        public float arcHeight, burst, scatterRadius, flatten;
        public int seed;
        public float stagger;
        public Order order;
        public Easing easing;
        public float spin, clumpStart, clumpEnd, alphaStart, alphaEnd, scaleStart, scaleEnd;
        public SweepShape sweepShape;
        public float sweepRadius;
        public SpeedCurve motionCurve, spinCurve, alphaCurve, scaleCurve, clumpCurve;
        public ClumpShape clumpShape;
        public float clumpJitter;

        public BlastSettings build() {
            return new BlastSettings(mode, driver, startTick, Math.max(1, duration), Math.clamp(progress, 0, 1),
                    trajectory, arcHeight, burst, Math.max(0, scatterRadius),
                    Math.clamp(flatten, 0, 1), seed, Math.clamp(stagger, 0, 0.95f), order, easing,
                    spin, Math.clamp(clumpStart, 1, 16), Math.clamp(clumpEnd, 1, 16),
                    Math.clamp(alphaStart, 0, 1), Math.clamp(alphaEnd, 0, 1), Math.max(0, scaleStart), Math.max(0, scaleEnd),
                    sweepShape, Math.max(0, sweepRadius), motionCurve, spinCurve, alphaCurve, scaleCurve, clumpCurve,
                    clumpShape, Math.clamp(clumpJitter, 0, 1));
        }
    }
}
