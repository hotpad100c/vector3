package ml.mypals.vectorthree.shape;

public record ShapeState(
        String shapeType, String shapeId,
        double x, double y, double z,
        float pitch, float yaw, float roll,
        double scaleX, double scaleY, double scaleZ,
        double sizeX, double sizeY, double sizeZ,
        boolean seeThrough, boolean visible
) {
    public static ShapeState cube(String id) {
        return new ShapeState("cube", id, 0, 0, 0, 0, 0, 0,
                1, 1, 1, 1, 1, 1, false, true);
    }

    public ShapeState interpolate(ShapeState target, double amount) {
        if (!shapeId.equals(target.shapeId) || !shapeType.equals(target.shapeType)) return this;
        return new ShapeState(shapeType, shapeId,
                lerp(x, target.x, amount), lerp(y, target.y, amount), lerp(z, target.z, amount),
                (float) lerp(pitch, target.pitch, amount), (float) lerp(yaw, target.yaw, amount),
                (float) lerp(roll, target.roll, amount),
                lerp(scaleX, target.scaleX, amount), lerp(scaleY, target.scaleY, amount),
                lerp(scaleZ, target.scaleZ, amount),
                lerp(sizeX, target.sizeX, amount), lerp(sizeY, target.sizeY, amount),
                lerp(sizeZ, target.sizeZ, amount),
                amount < 0.5 ? seeThrough : target.seeThrough,
                amount < 0.5 ? visible : target.visible);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
