package ml.mypals.vectorthree.shape.blast;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.playback.ReplayServer;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import ml.mypals.vectorthree.flashback.curve.SpeedCurve;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Where every block of a blast is at a given progress. Blocks are grouped by nested grids (clump sizes 16, 8, ... 1
 * blocks); a clump flies as one rigid piece until its size level ends, then its children carry on from where it was,
 * each towards its own scatter point, so the break-up is continuous. Everything is seeded, so any progress can be
 * evaluated on its own.
 */
public final class BlastMotion {
    private static final int MAX_LEVEL = 4;

    private final int count;
    private final int[] gx, gy, gz;
    private final Vector3f[] centers;
    private final Vector3f boundsMin = new Vector3f(Float.POSITIVE_INFINITY), boundsMax = new Vector3f(Float.NEGATIVE_INFINITY);
    private int builtSeed = Integer.MIN_VALUE;
    private int offsetX, offsetY, offsetZ;
    @SuppressWarnings("unchecked")
    private final Long2ObjectOpenHashMap<Vector3f>[] clumpCenters = new Long2ObjectOpenHashMap[MAX_LEVEL + 1];

    private record State(Vector3f position, Quaternionf rotation, Vector3f center) {}

    private Vector3f start = new Vector3f(), origin = new Vector3f(), scatter = new Vector3f();
    private Long2FloatOpenHashMap sweepTimes;

    /** {@code grid*}: each block's integer position from the region's min corner; {@code centers}: its local center. */
    public BlastMotion(int[] gridX, int[] gridY, int[] gridZ, Vector3f[] centers) {
        this.count = centers.length;
        this.gx = gridX;
        this.gy = gridY;
        this.gz = gridZ;
        this.centers = centers;
        for (Vector3f center : centers) {
            boundsMin.min(center);
            boundsMax.max(center);
        }
    }

    public static float progress(BlastSettings s) {
        float p;
        if (s.driver() == BlastSettings.Driver.MANUAL) {
            p = s.progress();
        } else {
            p = (float) ((replayTick() - s.startTick()) / Math.max(1, s.duration()));
        }
        p = Math.clamp(p, 0, 1);
        return s.mode() == BlastSettings.Mode.GATHER ? 1 - p : p;
    }

    private static double replayTick() {
        if (Flashback.isExporting()) return Flashback.EXPORT_JOB.getCurrentTickDouble();
        ReplayServer server = Flashback.getReplayServer();
        return server != null && !server.replayPaused ? server.getPartialReplayTick() : TimelineWindow.getCursorTick();
    }

    /**
     * Fills each block's local-to-local transform and alpha for progress {@code p}. The custom path runs from
     * {@code start} through {@code path}; the order and the explosion radiate from {@code origin}.
     */
    public void evaluate(BlastSettings s, Vector3f start, List<Vector3f> path, Vector3f origin, Vector3f sweepEnd,
            Vector3f scatter, float p, Matrix4f[] transforms, float[] alpha) {
        prepare(s.seed());
        this.scatter = scatter;
        this.start = start;
        this.origin = origin;
        sweepTimes = s.order() == BlastSettings.Order.SWEEP ? sweepTimes(s, origin, sweepEnd) : null;
        motionCurve = s.motion();
        spinCurve = s.curve(BlastSettings.Curve.SPIN);
        scaleCurve = s.curve(BlastSettings.Curve.SCALE);
        SpeedCurve alphaCurve = s.curve(BlastSettings.Curve.ALPHA);
        int top = level(Math.max(s.clumpStart(), 1));
        splits = splitTimes(s, top);
        Long2ObjectOpenHashMap<State> memo = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < count; i++) {
            long topKey = key(i, top);
            Vector3f topCenter = clumpCenters[top].get(topKey);
            float delay = delay(s, top, topKey, topCenter);
            float t = Float.isNaN(delay) ? 0 : Math.clamp((p - s.stagger() * delay) / (1 - s.stagger()), 0, 1);
            int level = top;
            while (level > 0 && splits[level - 1] <= t) level--;
            long key = key(i, level);
            State state = memo.get(memoKey(level, key));
            if (state == null) {
                state = state(s, path, level, top, i, t);
                memo.put(memoKey(level, key), state);
            }
            transforms[i].identity().translate(state.position()).rotate(state.rotation()).scale(scale(s, t))
                    .translate(-state.center().x, -state.center().y, -state.center().z);
            alpha[i] = Math.clamp(s.alphaStart() + (s.alphaEnd() - s.alphaStart()) * alphaCurve.evaluate(t), 0, 1);
        }
    }

    private SpeedCurve motionCurve, spinCurve, scaleCurve;
    private float[] splits = new float[MAX_LEVEL + 1];

    private float scale(BlastSettings s, float t) {
        return Math.max(0, s.scaleStart() + (s.scaleEnd() - s.scaleStart()) * scaleCurve.evaluate(t));
    }

    // splits[level]: the local time at which the clumps of that level break off from their parents, the first
    // time the clump size falls below 2^(level + 1); 2 if never. A clump that has split stays split.
    private static float[] splitTimes(BlastSettings s, int top) {
        SpeedCurve curve = s.curve(BlastSettings.Curve.CLUMP);
        float[] splits = new float[MAX_LEVEL + 1];
        java.util.Arrays.fill(splits, 2);
        for (int level = 0; level < top; level++) {
            float threshold = 1 << (level + 1);
            float previous = 0;
            for (int step = 1; step <= 64; step++) {
                float t = step / 64f;
                if (size(s, curve, t) < threshold) {
                    float low = previous, high = t;
                    for (int i = 0; i < 12; i++) {
                        float mid = (low + high) / 2;
                        if (size(s, curve, mid) < threshold) high = mid; else low = mid;
                    }
                    splits[level] = high;
                    break;
                }
                previous = t;
            }
        }
        for (int level = top - 2; level >= 0; level--) splits[level] = Math.max(splits[level], splits[level + 1]);
        return splits;
    }

    private static float size(BlastSettings s, SpeedCurve curve, float t) {
        return s.clumpStart() + (s.clumpEnd() - s.clumpStart()) * curve.evaluate(t);
    }

    // The clump holding block i at this level, at local time t (which the clump's root sets). Position follows the
    // motion curve along the trajectory, rotation the spin curve.
    private State state(BlastSettings s, List<Vector3f> path, int level, int top, int block, float t) {
        long key = key(block, level);
        Vector3f center = clumpCenters[level].get(key);
        Vector3f target = scatter(s, level, key);
        Vector3f axis = randomUnit(hash(s.seed(), level, key, 3));
        float tau = motionCurve.evaluate(t);
        if (level == top) {
            return new State(base(s, path, center, target, level, key, tau),
                    new Quaternionf().rotateAxis((float) Math.toRadians(s.spin() * spinCurve.evaluate(t)), axis), center);
        }
        float split = splits[level];
        if (t < split) return state(s, path, level + 1, top, block, t);
        State parent = state(s, path, level + 1, top, block, split);
        Vector3f start = new Vector3f(center).sub(parent.center()).mul(scale(s, split)).rotate(parent.rotation()).add(parent.position());
        float tauSplit = motionCurve.evaluate(split);
        float remaining = Math.abs(1 - tauSplit) > 1.0e-4f ? (tau - tauSplit) / (1 - tauSplit)
                : (t - split) / Math.max(1.0e-4f, 1 - split);
        Vector3f position = base(s, path, center, target, level, key, tau)
                .add(start.sub(base(s, path, center, target, level, key, tauSplit)).mul(1 - remaining));
        float turned = s.spin() * (spinCurve.evaluate(t) - spinCurve.evaluate(split));
        Quaternionf rotation = new Quaternionf().rotateAxis((float) Math.toRadians(turned), axis).mul(parent.rotation());
        return new State(position, rotation, center);
    }

    private Vector3f base(BlastSettings s, List<Vector3f> path, Vector3f from, Vector3f to, int level, long key, float t) {
        Vector3f position;
        if (s.trajectory() == BlastSettings.Trajectory.PATH) {
            Vector3f end = scatter;
            Vector3f curve = catmullRom(start, path, end, t);
            Vector3f offset = new Vector3f(from).sub(start).lerp(new Vector3f(to).sub(end), t);
            return curve.add(offset);
        }
        position = new Vector3f(from).lerp(to, t);
        if (s.trajectory() != BlastSettings.Trajectory.LINE) position.y += s.arcHeight() * 4 * t * (1 - t);
        if (s.trajectory() == BlastSettings.Trajectory.EXPLODE) {
            Vector3f radial = new Vector3f(from.x - origin.x, 0, from.z - origin.z);
            if (radial.lengthSquared() < 1.0e-4f) {
                float angle = random(hash(s.seed(), level, key, 5)) * (float) Math.PI * 2;
                radial.set((float) Math.cos(angle), 0, (float) Math.sin(angle));
            }
            position.add(radial.normalize().mul(s.burst() * (float) Math.sin(Math.PI * t)));
        }
        return position;
    }

    // Through the path start, the path points and the scatter center.
    private static Vector3f catmullRom(Vector3f start, List<Vector3f> path, Vector3f end, float t) {
        int n = path.size() + 2;
        Vector3f[] points = new Vector3f[n];
        points[0] = start;
        for (int i = 0; i < path.size(); i++) points[i + 1] = path.get(i);
        points[n - 1] = end;
        float scaled = t * (n - 1);
        int segment = Math.min(n - 2, (int) scaled);
        float u = scaled - segment;
        Vector3f p0 = points[Math.max(0, segment - 1)], p1 = points[segment], p2 = points[segment + 1];
        Vector3f p3 = points[Math.min(n - 1, segment + 2)];
        float u2 = u * u, u3 = u2 * u;
        return new Vector3f(
                0.5f * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3),
                0.5f * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3),
                0.5f * (2 * p1.z + (-p0.z + p2.z) * u + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * u2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * u3));
    }

    private Vector3f scatter(BlastSettings s, int level, long key) {
        Vector3f direction = randomUnit(hash(s.seed(), level, key, 1));
        float distance = s.scatterRadius() * (float) Math.cbrt(random(hash(s.seed(), level, key, 2)));
        direction.mul(distance);
        direction.y *= s.flatten();
        return direction.add(scatter);
    }

    // 0 moves first. Gathering plays the blast backwards, so there the order is flipped: the pieces that would
    // leave first are the first to settle into the shape.
    private float delay(BlastSettings s, int level, long key, Vector3f center) {
        float height = Math.max(1.0e-4f, boundsMax.y - boundsMin.y);
        float value = switch (s.order()) {
            case RANDOM -> random(hash(s.seed(), level, key, 4));
            case FROM_ORIGIN -> Math.clamp(center.distance(origin) / Math.max(1.0e-4f, reach(origin)), 0, 1);
            case SWEEP -> sweepTimes.get(key);
            case BOTTOM_UP -> Math.clamp((center.y - boundsMin.y) / height, 0, 1);
            case TOP_DOWN -> 1 - Math.clamp((center.y - boundsMin.y) / height, 0, 1);
        };
        if (Float.isNaN(value)) return value;
        return s.mode() == BlastSettings.Mode.GATHER ? 1 - value : value;
    }

    // Each top clump starts when the sweep first touches one of its blocks; untouched clumps stay (NaN).
    private Long2FloatOpenHashMap sweepTimes(BlastSettings s, Vector3f from, Vector3f to) {
        int top = level(Math.max(s.clumpStart(), 1));
        Long2FloatOpenHashMap times = new Long2FloatOpenHashMap();
        times.defaultReturnValue(Float.NaN);
        Vector3f d = new Vector3f(to).sub(from);
        for (int i = 0; i < count; i++) {
            float t = firstTouch(s, new Vector3f(centers[i]).sub(from), d);
            if (Float.isNaN(t)) continue;
            long key = key(i, top);
            float known = times.get(key);
            if (Float.isNaN(known) || t < known) times.put(key, t);
        }
        return times;
    }

    /** The first u in [0, 1] at which the sweep volume, centered at u * d, holds the point w; NaN if never. */
    static float firstTouch(BlastSettings s, Vector3f w, Vector3f d) {
        float r = s.sweepRadius();
        float lo = 0, hi = 1;
        if (s.sweepShape() == BlastSettings.SweepShape.CUBE) {
            for (int axis = 0; axis < 3; axis++) {
                float wi = w.get(axis), di = d.get(axis);
                if (Math.abs(di) < 1.0e-6f) {
                    if (Math.abs(wi) > r) return Float.NaN;
                    continue;
                }
                float a = (wi - r) / di, b = (wi + r) / di;
                lo = Math.max(lo, Math.min(a, b));
                hi = Math.min(hi, Math.max(a, b));
            }
            return lo <= hi ? lo : Float.NaN;
        }
        float dd = d.dot(d), wd = w.dot(d), ww = w.dot(w) - r * r;
        if (dd < 1.0e-8f) return ww <= 0 ? 0 : Float.NaN;
        float disc = wd * wd - dd * ww;
        if (disc < 0) return Float.NaN;
        float root = (float) Math.sqrt(disc);
        float first = (wd - root) / dd, last = (wd + root) / dd;
        if (last < 0 || first > 1) return Float.NaN;
        return Math.max(0, first);
    }

    /** Where the sweep is along its line at progress {@code p} (after BlastMotion#progress). */
    public static float sweepPosition(BlastSettings s, float p) {
        float real = s.mode() == BlastSettings.Mode.GATHER ? 1 - p : p;
        float stagger = s.stagger();
        if (stagger <= 1.0e-4f) return real > 0 ? 1 : 0;
        float u = s.mode() == BlastSettings.Mode.GATHER ? (real - (1 - stagger)) / stagger : real / stagger;
        return Math.clamp(u, 0, 1);
    }

    // The farthest corner of the blocks' bounds from a point.
    private float reach(Vector3f from) {
        float x = Math.max(Math.abs(boundsMin.x - from.x), Math.abs(boundsMax.x - from.x));
        float y = Math.max(Math.abs(boundsMin.y - from.y), Math.abs(boundsMax.y - from.y));
        float z = Math.max(Math.abs(boundsMin.z - from.z), Math.abs(boundsMax.z - from.z));
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static int level(float size) {
        return Math.clamp((int) Math.floor(Math.log(size) / Math.log(2) + 1.0e-4), 0, MAX_LEVEL);
    }

    // The grids share one seeded origin, so every clump lies inside one clump of the level above.
    private void prepare(int seed) {
        if (builtSeed == seed) return;
        builtSeed = seed;
        long h = hash(seed, 9, 0, 0);
        offsetX = (int) (h & 15);
        offsetY = (int) (h >>> 4 & 15);
        offsetZ = (int) (h >>> 8 & 15);
        for (int level = 0; level <= MAX_LEVEL; level++) {
            Long2ObjectOpenHashMap<float[]> sums = new Long2ObjectOpenHashMap<>();
            for (int i = 0; i < count; i++) {
                float[] sum = sums.computeIfAbsent(key(i, level), k -> new float[4]);
                sum[0] += centers[i].x;
                sum[1] += centers[i].y;
                sum[2] += centers[i].z;
                sum[3]++;
            }
            Long2ObjectOpenHashMap<Vector3f> result = new Long2ObjectOpenHashMap<>();
            sums.forEach((k, sum) -> result.put((long) k, new Vector3f(sum[0] / sum[3], sum[1] / sum[3], sum[2] / sum[3])));
            clumpCenters[level] = result;
        }
    }

    private long key(int block, int level) {
        long x = Math.floorDiv(gx[block] + offsetX, 1 << level) & 0x1FFFFF;
        long y = Math.floorDiv(gy[block] + offsetY, 1 << level) & 0x1FFFFF;
        long z = Math.floorDiv(gz[block] + offsetZ, 1 << level) & 0x1FFFFF;
        return x | y << 21 | z << 42;
    }

    private static long memoKey(int level, long key) {
        return key * 31 + level;
    }

    private static long hash(int seed, int level, long key, int salt) {
        long z = key * 0x9E3779B97F4A7C15L + seed * 0xBF58476D1CE4E5B9L + level * 0x94D049BB133111EBL + salt * 0x2545F4914F6CDD1DL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float random(long hash) {
        return (hash >>> 40) / (float) (1L << 24);
    }

    private static Vector3f randomUnit(long hash) {
        float z = random(hash) * 2 - 1;
        float phi = random(hash * 0x9E3779B97F4A7C15L + 1) * (float) Math.PI * 2;
        float r = (float) Math.sqrt(1 - z * z);
        return new Vector3f(r * (float) Math.cos(phi), z, r * (float) Math.sin(phi));
    }
}
