package ml.mypals.vectorthree.core.light;

import net.minecraft.world.phys.Vec3;

public record Light(Vec3 position, float red, float green, float blue, float intensity, float radius,
                    float volume, float shadow) {
    public static Light defaults(Vec3 position) {
        return new Light(position, 1, 0.75f, 0.5f, 1, 8, 0.2f, 0.6f);
    }

    public Light sanitized() {
        return new Light(position == null ? Vec3.ZERO : position,
                Math.clamp(red, 0, 1), Math.clamp(green, 0, 1), Math.clamp(blue, 0, 1),
                Math.clamp(intensity, 0, 8), Math.clamp(radius, 0.1f, 128),
                Math.clamp(volume, 0, 2), Math.clamp(shadow, 0, 1));
    }

    public Light lerp(Light to, float amount) {
        return new Light(position.lerp(to.position, amount),
                red + (to.red - red) * amount, green + (to.green - green) * amount,
                blue + (to.blue - blue) * amount, intensity + (to.intensity - intensity) * amount,
                radius + (to.radius - radius) * amount,
                volume + (to.volume - volume) * amount,
                shadow + (to.shadow - shadow) * amount);
    }
}
