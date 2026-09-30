package ml.mypals.vectorthree.core.light;

import net.minecraft.world.phys.Vec3;

public record Light(Vec3 position, float red, float green, float blue, float intensity, float radius,
                    float volume, float shadow, Type type, Vec3 direction, float areaWidth, float areaHeight,
                    float innerAngle, float outerAngle, float areaReach) {
    public static final float MAX_INTENSITY = 100;
    public enum Type { POINT, AREA, SPOT }

    public static Light defaults(Vec3 position) {
        return new Light(position, 1, 0.75f, 0.5f, 1, 8, 0.2f, 0.6f,
                Type.POINT, new Vec3(0, -1, 0), 2, 2, 20, 35, 8);
    }

    public Light sanitized() {
        boolean legacy = type == null;
        Vec3 axis = direction == null || direction.lengthSqr() < 1e-8
                ? new Vec3(0, -1, 0) : direction.normalize();
        float outer = Math.clamp(legacy ? 35 : outerAngle, 1, 89);
        float inner = Math.clamp(legacy ? 20 : innerAngle, 0, outer);
        return new Light(position == null ? Vec3.ZERO : position,
                Math.clamp(red, 0, 1), Math.clamp(green, 0, 1), Math.clamp(blue, 0, 1),
                Math.clamp(intensity, 0, MAX_INTENSITY), Math.clamp(radius, 0.1f, 128),
                Math.clamp(volume, 0, 2), Math.clamp(shadow, 0, 1),
                type == null ? Type.POINT : type, axis,
                Math.clamp(legacy ? 2 : areaWidth, 0.1f, 64),
                Math.clamp(legacy ? 2 : areaHeight, 0.1f, 64), inner, outer,
                Math.clamp(areaReach <= 0 ? radius : areaReach, 0.1f, 128));
    }

    public Light lerp(Light to, float amount) {
        Vec3 axis = direction.lerp(to.direction, amount);
        if (axis.lengthSqr() < 1e-8) axis = amount < 0.5f ? direction : to.direction;
        return new Light(position.lerp(to.position, amount),
                red + (to.red - red) * amount, green + (to.green - green) * amount,
                blue + (to.blue - blue) * amount, intensity + (to.intensity - intensity) * amount,
                radius + (to.radius - radius) * amount,
                volume + (to.volume - volume) * amount,
                shadow + (to.shadow - shadow) * amount,
                amount < 0.5f ? type : to.type, axis.normalize(),
                areaWidth + (to.areaWidth - areaWidth) * amount,
                areaHeight + (to.areaHeight - areaHeight) * amount,
                innerAngle + (to.innerAngle - innerAngle) * amount,
                outerAngle + (to.outerAngle - outerAngle) * amount,
                areaReach + (to.areaReach - areaReach) * amount).sanitized();
    }
}
