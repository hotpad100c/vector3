package ml.mypals.vectorthree.flashback.fade.effects;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Where the overworld's brighter light source, the sun by day or the moon by night, is on the screen. */
public final class SunScreen {
    public enum Kind { SUN, MOON }

    /** {@code x}, {@code y}: screen position in 0..1 (it may be outside); {@code fade}: how much of it should show. */
    public record Light(Kind kind, float x, float y, float fade) {}

    private SunScreen() {}

    public static @Nullable Light locate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD
                || !DepthOfFieldEffect.hasCapturedDepth()) return null;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = minecraft.gameRenderer.mainCamera();
        float rain = 1 - minecraft.level.getRainLevel(partialTick);
        Vector3f sun = direction(camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick));
        Vector3f moon = direction(camera.attributeProbe().getValue(EnvironmentAttributes.MOON_ANGLE, partialTick));
        float daylight = elevation(sun.y);
        MoonPhase phase = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, partialTick);
        int step = phase.index();
        float moonlight = 1 - Math.min(step, MoonPhase.COUNT - step) / 4f;

        Light sunLight = project(camera, Kind.SUN, sun, daylight * rain);
        Light moonLight = project(camera, Kind.MOON, moon, elevation(moon.y) * (1 - daylight) * moonlight * rain);
        if (sunLight == null) return moonLight;
        return moonLight != null && moonLight.fade() > sunLight.fade() ? moonLight : sunLight;
    }

    private static Vector3f direction(float degrees) {
        double angle = Math.toRadians(degrees);
        return new Vector3f(-(float) Math.sin(angle), (float) Math.cos(angle), 0);
    }

    private static float elevation(float y) {
        return Math.clamp((y + 0.04f) / 0.12f, 0, 1);
    }

    private static @Nullable Light project(Camera camera, Kind kind, Vector3f direction, float strength) {
        float forward = direction.dot(camera.forwardVector());
        if (forward <= 0.01f || strength <= 0.001f) return null;
        float x = 0.5f + direction.dot(new Vector3f(camera.leftVector()).negate())
                * DepthOfFieldEffect.projectionX() / (2 * forward);
        float y = 0.5f + direction.dot(camera.upVector()) * DepthOfFieldEffect.projectionY() / (2 * forward);
        // Full strength up to the screen edge, gone once the light is a screen-width beyond it.
        float outside = Math.clamp(Math.max(Math.abs(x - 0.5f), Math.abs(y - 0.5f)) * 2 - 1, 0, 1);
        float fade = strength * (1 - outside * outside * (3 - 2 * outside));
        return fade <= 0.001f ? null : new Light(kind, x, y, fade);
    }
}
