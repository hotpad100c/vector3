package ml.mypals.vectorthree.shape;

import java.util.ArrayList;
import java.util.List;

public record ShapeState(
        String shapeType, String shapeId,
        double x, double y, double z,
        float pitch, float yaw, float roll,
        double scaleX, double scaleY, double scaleZ,
        double sizeX, double sizeY, double sizeZ,
        int segments, float lineWidth,
        int color,
        List<ShapePoint> points,
        boolean seeThrough, boolean visible
) {
    public static ShapeState create(String type, String id) {
        List<ShapePoint> points = switch (type) {
            case "line", "line_strip" -> List.of(new ShapePoint(0, 0, 0), new ShapePoint(1, 1, 1));
            default -> List.of();
        };
        return new ShapeState(type, id, 0, 0, 0, 0, 0, 0,
                1, 1, 1, 1, 1, 1, 32, 0.05f, 0xFFFFFFFF, points, false, true);
    }

    public ShapeState interpolate(ShapeState target, double amount) {
        return new ShapeState(shapeType, shapeId,
                lerp(x, target.x, amount), lerp(y, target.y, amount), lerp(z, target.z, amount),
                (float) lerp(pitch, target.pitch, amount), (float) lerp(yaw, target.yaw, amount),
                (float) lerp(roll, target.roll, amount),
                lerp(scaleX, target.scaleX, amount), lerp(scaleY, target.scaleY, amount),
                lerp(scaleZ, target.scaleZ, amount),
                lerp(sizeX, target.sizeX, amount), lerp(sizeY, target.sizeY, amount),
                lerp(sizeZ, target.sizeZ, amount),
                (int) Math.round(lerp(segments, target.segments, amount)),
                (float) lerp(lineWidth, target.lineWidth, amount),
                lerpColor(color, target.color, amount),
                interpolatePoints(target, amount),
                amount < 0.5 ? seeThrough : target.seeThrough,
                amount < 0.5 ? visible : target.visible);
    }

    public ShapeState with(float[] position, float[] rotation, float[] scale, float[] size,
            int segments, float lineWidth, int color, List<ShapePoint> points,
            boolean seeThrough, boolean visible) {
        return new ShapeState(shapeType, shapeId,
                position[0], position[1], position[2], rotation[0], rotation[1], rotation[2],
                scale[0], scale[1], scale[2], size[0], size[1], size[2],
                segments, lineWidth, color, List.copyOf(points), seeThrough, visible);
    }

    public ShapeState withIdentity(String shapeType, String shapeId) {
        return new ShapeState(shapeType, shapeId, x, y, z, pitch, yaw, roll,
                scaleX, scaleY, scaleZ, sizeX, sizeY, sizeZ, segments, lineWidth,
                color, points == null ? List.of() : points, seeThrough, visible);
    }

    private List<ShapePoint> interpolatePoints(ShapeState target, double amount) {
        if (points == null || target.points == null || points.size() != target.points.size()) {
            return amount < 0.5 ? points : target.points;
        }
        List<ShapePoint> result = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) result.add(points.get(i).interpolate(target.points.get(i), amount));
        return result;
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static int lerpColor(int from, int to, double amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int channel = (int) Math.round(lerp((from >>> shift) & 255, (to >>> shift) & 255, amount));
            result |= Math.clamp(channel, 0, 255) << shift;
        }
        return result;
    }
}
