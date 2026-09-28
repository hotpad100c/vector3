package ml.mypals.vectorthree.flashback.fade.effects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ColorCurve(List<Point> points) {
    public record Point(float x, float y) {}

    public static ColorCurve identity() { return new ColorCurve(List.of(new Point(0, 0), new Point(1, 1))); }
    public static ColorCurve neutral() { return new ColorCurve(List.of(new Point(0, 0.5f), new Point(1, 0.5f))); }

    public ColorCurve sanitized(float endpoint) {
        if (points == null) return endpoint == 0.5f ? neutral() : identity();
        List<Point> sorted = new ArrayList<>();
        for (Point point : points) {
            if (point != null && Float.isFinite(point.x) && Float.isFinite(point.y))
                sorted.add(new Point(Math.clamp(point.x, 0, 1), Math.clamp(point.y, 0, 1)));
        }
        sorted.sort(Comparator.comparingDouble(Point::x));
        List<Point> result = new ArrayList<>();
        result.add(new Point(0, sorted.isEmpty() ? endpoint : sorted.getFirst().y));
        for (Point point : sorted) {
            if (point.x > 0.01f && point.x < 0.99f && result.size() < 15
                    && point.x - result.getLast().x > 0.01f) result.add(point);
        }
        result.add(new Point(1, sorted.isEmpty() ? endpoint : sorted.getLast().y));
        return new ColorCurve(List.copyOf(result));
    }

    public float evaluate(float x) {
        x = Math.clamp(x, 0, 1);
        if (x <= points.getFirst().x) return points.getFirst().y;
        for (int i = 1; i < points.size(); i++) {
            Point b = points.get(i);
            if (x <= b.x) {
                Point a = points.get(i - 1);
                return a.y + (b.y - a.y) * (x - a.x) / Math.max(0.0001f, b.x - a.x);
            }
        }
        return points.getLast().y;
    }

    public ColorCurve lerp(ColorCurve other, float t) {
        List<Point> sampled = new ArrayList<>(17);
        for (int i = 0; i <= 16; i++) {
            float x = i / 16f;
            sampled.add(new Point(x, evaluate(x) + (other.evaluate(x) - evaluate(x)) * t));
        }
        return new ColorCurve(List.copyOf(sampled));
    }
}
