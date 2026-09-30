package ml.mypals.vectorthree.core.light;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }

    @Test
    void spotLightAllowsZeroInnerAngle() {
        Light base = Light.defaults(net.minecraft.world.phys.Vec3.ZERO);
        Light spot = new Light(base.position(), base.red(), base.green(), base.blue(), base.intensity(),
                base.radius(), base.volume(), base.shadow(), Light.Type.SPOT, base.direction(),
                base.areaWidth(), base.areaHeight(), 0, 30).sanitized();

        assertEquals(0, spot.innerAngle());
        assertEquals(30, spot.outerAngle());
    }
}
