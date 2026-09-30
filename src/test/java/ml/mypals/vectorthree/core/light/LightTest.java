package ml.mypals.vectorthree.core.light;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightTest {
    @Test
    void oldKeyframeDefaultsToPointLight() {
        String json = """
                {"position":{"x":1,"y":2,"z":3},"red":1,"green":0.75,"blue":0.5,
                 "intensity":1,"radius":5,"volume":0.2,"shadow":0.6}
                """;
        Light light = new Gson().fromJson(json, Light.class).sanitized();

        assertEquals(Light.Type.POINT, light.type());
        assertEquals(5, light.radius());
        assertEquals(2, light.areaWidth());
        assertEquals(20, light.innerAngle());
        assertEquals(35, light.outerAngle());
        assertEquals(5, light.areaReach());
    }

    @Test
    void spotLightAllowsZeroInnerAngle() {
        Light base = Light.defaults(net.minecraft.world.phys.Vec3.ZERO);
        Light spot = new Light(base.position(), base.red(), base.green(), base.blue(), base.intensity(),
                base.radius(), base.volume(), base.shadow(), Light.Type.SPOT, base.direction(),
                base.areaWidth(), base.areaHeight(), 0, 30, base.areaReach(), "").sanitized();

        assertEquals(0, spot.innerAngle());
        assertEquals(30, spot.outerAngle());
    }

    @Test
    void intensityAboveEightAndLegacyAreaReachSurvive() {
        String json = """
                {"position":{"x":0,"y":5,"z":0},"red":1,"green":1,"blue":1,
                 "intensity":25,"radius":12,"volume":0,"shadow":0,"type":"AREA",
                 "direction":{"x":0,"y":-1,"z":0},"areaWidth":4,"areaHeight":3,
                 "innerAngle":20,"outerAngle":35}
                """;
        Light light = new Gson().fromJson(json, Light.class).sanitized();

        assertEquals(25, light.intensity());
        assertEquals(12, light.areaReach());
    }

    @Test
    void oldKeyframeHasNoParent() {
        Light light = new Gson().fromJson("{\"radius\":5}", Light.class).sanitized();

        assertEquals("", light.parent());
        assertFalse(light.hasParent());
    }

    @Test
    void parentSurvivesSavingAndLoading() {
        Light parented = Light.defaults(net.minecraft.world.phys.Vec3.ZERO).withParent("vector3:lamp");
        Gson gson = new Gson();
        Light loaded = gson.fromJson(gson.toJson(parented), Light.class).sanitized();

        assertEquals("vector3:lamp", loaded.parent());
        assertTrue(loaded.hasParent());
    }

    @Test
    void blendingLightsOfOneParentKeepsIt() {
        Light a = Light.defaults(net.minecraft.world.phys.Vec3.ZERO).withParent("vector3:lamp");
        Light b = a.placed(new net.minecraft.world.phys.Vec3(4, 0, 0), a.direction(), 1, "vector3:lamp");
        Light middle = a.lerp(b, 0.5f);

        assertEquals("vector3:lamp", middle.parent());
        assertEquals(2, middle.position().x, 1.0e-6);
    }

    @Test
    void movingIntoAnotherSpaceScalesSizes() {
        Light light = Light.defaults(net.minecraft.world.phys.Vec3.ZERO);
        Light placed = light.placed(new net.minecraft.world.phys.Vec3(1, 2, 3), light.direction(), 2, "vector3:lamp");

        assertEquals(light.radius() * 2, placed.radius(), 1.0e-6);
        assertEquals(light.areaWidth() * 2, placed.areaWidth(), 1.0e-6);
        assertEquals("vector3:lamp", placed.parent());
    }
}
