package ml.mypals.vectorthree.shape.arrow;

import ml.mypals.ryansrenderingkit.shape.Shape;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.List;

public final class ArrowShape extends Shape {
    private Vec3 start;
    private Vec3 end;
    private float width;
    private float headSize;

    public ArrowShape(Vec3 start, Vec3 end, float width, float headSize, Color color, boolean seeThrough) {
        super(RenderingType.BATCH, transformer -> {}, color, Vec3.ZERO, seeThrough);
        this.start = start;
        this.end = end;
        this.width = width;
        this.headSize = headSize;
        generateRawGeometry(false);
    }

    public void forceSet(Vec3 start, Vec3 end, float width, float headSize) {
        if (this.start.equals(start) && this.end.equals(end)
                && this.width == width && this.headSize == headSize) return;
        this.start = start;
        this.end = end;
        this.width = width;
        this.headSize = headSize;
        generateRawGeometry(true);
    }

    @Override
    protected void generateRawGeometry(boolean regenerate) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0e-6) {
            modelVertexes = List.of(start, start, start);
            indexBuffer = new int[] {0, 1, 2};
            return;
        }

        Vec3 direction = delta.scale(1 / length);
        Vec3 reference = Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = side.cross(direction).normalize();
        double shaftRadius = Math.max(0.001, width * 0.5);
        double safeHeadSize = Math.max(0.01, headSize);
        double baseHeadLength = Math.max(length * 0.2, width * 4);
        double headLength = Math.min(length * 0.9, baseHeadLength * safeHeadSize);
        double headRadius = Math.max(shaftRadius * 1.01,
                Math.max(shaftRadius * 2.5, baseHeadLength * 0.35) * safeHeadSize);
        Vec3 headBase = end.subtract(direction.scale(headLength));

        modelVertexes = List.of(
                corner(start, side, up, shaftRadius, shaftRadius),
                corner(start, side, up, -shaftRadius, shaftRadius),
                corner(start, side, up, -shaftRadius, -shaftRadius),
                corner(start, side, up, shaftRadius, -shaftRadius),
                corner(headBase, side, up, shaftRadius, shaftRadius),
                corner(headBase, side, up, -shaftRadius, shaftRadius),
                corner(headBase, side, up, -shaftRadius, -shaftRadius),
                corner(headBase, side, up, shaftRadius, -shaftRadius),
                corner(headBase, side, up, headRadius, headRadius),
                corner(headBase, side, up, -headRadius, headRadius),
                corner(headBase, side, up, -headRadius, -headRadius),
                corner(headBase, side, up, headRadius, -headRadius), end);
        indexBuffer = new int[] {
                0, 4, 5, 0, 5, 1, 1, 5, 6, 1, 6, 2,
                2, 6, 7, 2, 7, 3, 3, 7, 4, 3, 4, 0,
                0, 1, 2, 0, 2, 3,
                8, 12, 9, 9, 12, 10, 10, 12, 11, 11, 12, 8,
                8, 9, 10, 8, 10, 11
        };
    }

    private static Vec3 corner(Vec3 center, Vec3 side, Vec3 up, double sideScale, double upScale) {
        return center.add(side.scale(sideScale)).add(up.scale(upScale));
    }
}
