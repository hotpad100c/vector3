package ml.mypals.vectorthree.fb.light;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.core.light.Light;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeType;
import ml.mypals.vectorthree.mc.light.LightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;

public final class LightKeyframeType extends CustomKeyframeType<Light> {
    public static final LightKeyframeType INSTANCE = new LightKeyframeType();

    private LightKeyframeType() {
        super("vector3_light", "vector3.keyframe_type.light", Light.class);
    }

    @Override public String icon() { return null; }

    @Override protected Light createValue() {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 position = camera.isInitialized()
                ? camera.position().add(new Vec3(camera.forwardVector()).scale(5)) : Vec3.ZERO;
        return Light.defaults(position);
    }

    @Override protected Light sanitize(Light value) { return value.sanitized(); }
    @Override protected Light lerp(Light from, Light to, double amount) { return from.lerp(to, (float) amount); }
    @Override protected void apply(Light value, KeyframeHandler handler) { LightRenderer.request(value); }

    @Override protected Light edit(Light value) {
        float[] position = {(float) value.position().x, (float) value.position().y, (float) value.position().z};
        float[] color = {value.red(), value.green(), value.blue()};
        float[] intensity = {value.intensity()}, radius = {value.radius()};
        float[] volume = {value.volume()}, shadow = {value.shadow()};
        boolean changed = ImGui.dragFloat3(I18n.get("vector3.light.position"), position, 0.05f);
        changed |= ImGui.colorEdit3(I18n.get("vector3.light.color"), color);
        changed |= ImGui.dragFloat(I18n.get("vector3.light.intensity"), intensity, 0.02f, 0, 8);
        changed |= ImGui.dragFloat(I18n.get("vector3.light.radius"), radius, 0.05f, 0.1f, 128);
        changed |= ImGui.sliderFloat(I18n.get("vector3.light.volume"), volume, 0, 2);
        changed |= ImGui.sliderFloat(I18n.get("vector3.light.shadow"), shadow, 0, 1);
        if (!changed) return value;
        return new Light(new Vec3(position[0], position[1], position[2]), color[0], color[1], color[2],
                intensity[0], radius[0], volume[0], shadow[0]).sanitized();
    }
}
