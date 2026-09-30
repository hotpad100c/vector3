package ml.mypals.vectorthree.fb.light;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.core.light.Light;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeType;
import ml.mypals.vectorthree.fb.editor.VectorIcons;
import ml.mypals.vectorthree.fb.shape.GizmoMode;
import ml.mypals.vectorthree.mc.light.LightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;

public final class LightKeyframeType extends CustomKeyframeType<Light> {
    public static final LightKeyframeType INSTANCE = new LightKeyframeType();

    private LightKeyframeType() {
        super("vector3_light", "vector3.keyframe_type.light", Light.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.LIGHT_TRACK, null); }

    @Override protected Light createValue() {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 position = camera.isInitialized()
                ? camera.position().add(new Vec3(camera.forwardVector()).scale(5)) : Vec3.ZERO;
        Light defaults = Light.defaults(position);
        if (!camera.isInitialized()) return defaults;
        return new Light(defaults.position(), defaults.red(), defaults.green(), defaults.blue(),
                defaults.intensity(), defaults.radius(), defaults.volume(), defaults.shadow(),
                defaults.type(), new Vec3(camera.forwardVector()), defaults.areaWidth(), defaults.areaHeight(),
                defaults.innerAngle(), defaults.outerAngle(), defaults.areaReach());
    }

    @Override protected Light sanitize(Light value) { return value.sanitized(); }
    @Override protected Light lerp(Light from, Light to, double amount) { return from.lerp(to, (float) amount); }
    @Override protected void apply(Light value, KeyframeHandler handler) { LightRenderer.request(value); }

    @Override protected Light edit(Light value) {
        Light original = value;
        value = value.sanitized();
        ImGui.text(I18n.get("vector3.gizmo.viewport_gizmo"));
        modeButton(GizmoMode.MOVE, "vector3.gizmo.move"); ImGui.sameLine();
        modeButton(GizmoMode.ROTATE, "vector3.gizmo.rotate"); ImGui.sameLine();
        modeButton(GizmoMode.SCALE, "vector3.gizmo.scale"); ImGui.sameLine();
        modeButton(GizmoMode.GEOMETRY, "vector3.gizmo.geometry");
        ImGui.textDisabled(I18n.get("vector3.gizmo.place_hint"));
        float[] position = {(float) value.position().x, (float) value.position().y, (float) value.position().z};
        float[] color = {value.red(), value.green(), value.blue()};
        float[] intensity = {value.intensity()}, radius = {value.radius()};
        float[] volume = {value.volume()}, shadow = {value.shadow()};
        Light.Type type = value.type();
        boolean changed = false;
        if (ImGui.beginCombo(I18n.get("vector3.light.type"), typeLabel(type))) {
            for (Light.Type option : Light.Type.values()) {
                if (ImGui.selectable(typeLabel(option), option == type)) {
                    type = option;
                    changed = true;
                }
            }
            ImGui.endCombo();
        }
        changed |= ImGui.dragFloat3(I18n.get("vector3.light.position"), position, 0.05f);
        changed |= ImGui.colorEdit3(I18n.get("vector3.light.color"), color);
        changed |= ImGui.dragFloat(I18n.get("vector3.light.intensity"), intensity, 0.02f, 0, Light.MAX_INTENSITY);
        changed |= ImGui.dragFloat(I18n.get("vector3.light.radius"), radius, 0.05f, 0.1f, 128);
        Vec3 direction = value.direction();
        float[] angles = {(float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
                (float) Math.toDegrees(Math.asin(Math.clamp(-direction.y, -1, 1)))};
        float[] width = {value.areaWidth()}, height = {value.areaHeight()};
        float[] reach = {value.areaReach()};
        float[] inner = {value.innerAngle()}, outer = {value.outerAngle()};
        if (type != Light.Type.POINT) {
            changed |= ImGui.dragFloat(I18n.get("vector3.light.yaw"), angles, 0.5f, -180, 180);
            float[] pitch = {angles[1]};
            changed |= ImGui.dragFloat(I18n.get("vector3.light.pitch"), pitch, 0.5f, -90, 90);
            angles[1] = pitch[0];
        }
        if (type == Light.Type.AREA) {
            changed |= ImGui.dragFloat(I18n.get("vector3.light.area_width"), width, 0.05f, 0.1f, 64);
            changed |= ImGui.dragFloat(I18n.get("vector3.light.area_height"), height, 0.05f, 0.1f, 64);
            changed |= ImGui.dragFloat(I18n.get("vector3.light.area_reach"), reach, 0.05f, 0.1f, 128);
        } else if (type == Light.Type.SPOT) {
            changed |= ImGui.dragFloat(I18n.get("vector3.light.inner_angle"), inner, 0.5f, 0, 89);
            changed |= ImGui.dragFloat(I18n.get("vector3.light.outer_angle"), outer, 0.5f, 1, 89);
        }
        changed |= ImGui.sliderFloat(I18n.get("vector3.light.volume"), volume, 0, 2);
        changed |= ImGui.sliderFloat(I18n.get("vector3.light.shadow"), shadow, 0, 1);
        if (!changed) return original;
        double yaw = Math.toRadians(angles[0]), pitch = Math.toRadians(angles[1]);
        Vec3 axis = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        return new Light(new Vec3(position[0], position[1], position[2]), color[0], color[1], color[2],
                intensity[0], radius[0], volume[0], shadow[0], type, axis,
                width[0], height[0], inner[0], outer[0], reach[0]).sanitized();
    }

    private static String typeLabel(Light.Type type) {
        return I18n.get("vector3.light.type." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static void modeButton(GizmoMode mode, String label) {
        if (ImGui.radioButton(I18n.get(label) + "##light_" + mode.name(), GizmoMode.current() == mode))
            GizmoMode.set(mode);
    }
}
