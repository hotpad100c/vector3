package ml.mypals.vectorthree.flashback.curve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A keyframe's speed curve: maps the linear time fraction through a segment (x) to progress (y). Points are
 * joined by cubic Béziers whose handles are stored relative to their point. The first and last points sit
 * at (0,0) and (1,1); y may leave 0..1 for overshoot and anticipation.
 */
public record SpeedCurve(List<Point> points) {
    public record Point(float x, float y, float inDx, float inDy, float outDx, float outDy) {
        public Point withPosition(float x, float y) {
            return new Point(x, y, inDx, inDy, outDx, outDy);
        }

        public Point withIn(float dx, float dy) {
            return new Point(x, y, dx, dy, outDx, outDy);
        }

        public Point withOut(float dx, float dy) {
            return new Point(x, y, inDx, inDy, dx, dy);
        }
    }

    public enum Preset {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE
    }

    public static SpeedCurve preset(Preset preset) {
        return switch (preset) {
            case LINEAR -> of(new Point(0, 0, 0, 0, 1 / 3f, 1 / 3f), new Point(1, 1, -1 / 3f, -1 / 3f, 0, 0));
            case EASE_IN -> of(new Point(0, 0, 0, 0, 0.42f, 0), new Point(1, 1, -1 / 3f, -1 / 3f, 0, 0));
            case EASE_OUT -> of(new Point(0, 0, 0, 0, 1 / 3f, 1 / 3f), new Point(1, 1, -0.42f, 0, 0, 0));
            case EASE_IN_OUT -> of(new Point(0, 0, 0, 0, 0.42f, 0), new Point(1, 1, -0.42f, 0, 0, 0));
            case BOUNCE -> of(new Point(0, 0, 0, 0, 0.25f, 0.6f),
                    new Point(0.6f, 1.12f, -0.15f, 0, 0.15f, 0),
                    new Point(1, 1, -0.15f, 0, 0, 0));
        };
    }

    private static SpeedCurve of(Point... points) {
        return new SpeedCurve(List.of(points));
    }

    /** Sorted points, fixed ends, and handles kept inside their segment so each x maps to one y. */
    public SpeedCurve sanitized() {
        List<Point> sorted = new ArrayList<>(points == null ? List.of() : points);
        sorted.sort(Comparator.comparingDouble(Point::x));
        if (sorted.isEmpty() || sorted.getFirst().x() > 0) sorted.addFirst(new Point(0, 0, 0, 0, 1 / 3f, 1 / 3f));
        if (sorted.getLast().x() < 1 || sorted.size() == 1) sorted.addLast(new Point(1, 1, -1 / 3f, -1 / 3f, 0, 0));
        Point first = sorted.getFirst(), last = sorted.getLast();
        sorted.set(0, new Point(0, 0, 0, 0, first.outDx(), first.outDy()));
        sorted.set(sorted.size() - 1, new Point(1, 1, last.inDx(), last.inDy(), 0, 0));
        List<Point> clamped = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            Point point = sorted.get(i);
            float before = i == 0 ? 0 : point.x() - sorted.get(i - 1).x();
            float after = i == sorted.size() - 1 ? 0 : sorted.get(i + 1).x() - point.x();
            clamped.add(new Point(point.x(), point.y(),
                    Math.clamp(point.inDx(), -before, 0), point.inDy(),
                    Math.clamp(point.outDx(), 0, after), point.outDy()));
        }
        return new SpeedCurve(List.copyOf(clamped));
    }

    public float evaluate(float t) {
        if (points.size() < 2) return t;
        t = Math.clamp(t, 0, 1);
        int segment = 0;
        while (segment < points.size() - 2 && t > points.get(segment + 1).x()) segment++;
        Point a = points.get(segment), b = points.get(segment + 1);
        float x0 = a.x(), x1 = a.x() + a.outDx(), x2 = b.x() + b.inDx(), x3 = b.x();
        float u = solve(t, x0, x1, x2, x3);
        return bezier(u, a.y(), a.y() + a.outDy(), b.y() + b.inDy(), b.y());
    }

    // The Bézier parameter whose x is t: Newton steps, falling back to bisection when they stall.
    private static float solve(float t, float x0, float x1, float x2, float x3) {
        if (x3 - x0 < 1.0e-6f) return 0;
        float u = (t - x0) / (x3 - x0);
        for (int i = 0; i < 8; i++) {
            float error = bezier(u, x0, x1, x2, x3) - t;
            if (Math.abs(error) < 1.0e-5f) return u;
            float slope = derivative(u, x0, x1, x2, x3);
            if (Math.abs(slope) < 1.0e-6f) break;
            u = Math.clamp(u - error / slope, 0, 1);
        }
        float low = 0, high = 1;
        for (int i = 0; i < 30; i++) {
            u = (low + high) / 2;
            if (bezier(u, x0, x1, x2, x3) < t) low = u; else high = u;
        }
        return u;
    }

    static float bezier(float u, float p0, float p1, float p2, float p3) {
        float v = 1 - u;
        return v * v * v * p0 + 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u * p3;
    }

    private static float derivative(float u, float p0, float p1, float p2, float p3) {
        float v = 1 - u;
        return 3 * v * v * (p1 - p0) + 6 * v * u * (p2 - p1) + 3 * u * u * (p3 - p2);
    }
}
