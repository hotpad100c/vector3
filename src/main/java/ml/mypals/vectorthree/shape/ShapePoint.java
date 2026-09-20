package ml.mypals.vectorthree.shape;

import net.minecraft.world.phys.Vec3;

public record ShapePoint(double x, double y, double z) {
    public Vec3 vec3() { return new Vec3(x, y, z); }

    public ShapePoint interpolate(ShapePoint target, double amount) {
        return new ShapePoint(x + (target.x - x) * amount,
                y + (target.y - y) * amount, z + (target.z - z) * amount);
    }
}
