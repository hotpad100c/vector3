package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import ml.mypals.vectorthree.camera.dolly.DollyZoomKeyframeType;
import ml.mypals.vectorthree.camera.target.Target;
import ml.mypals.vectorthree.camera.target.TargetEditor;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PrefabTemplates {
    public interface Template {
        String id();

        String nameKey();

        boolean edit();

        Prefab build();
    }

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();

    static {
        register(new Orbit());
        register(new DollyZoom());
    }

    private PrefabTemplates() {}

    public static void register(Template template) {
        TEMPLATES.put(template.id(), template);
    }

    public static Iterable<Template> all() {
        return TEMPLATES.values();
    }

    public static Template get(String id) {
        return TEMPLATES.get(id);
    }

    private static Prefab.Track track(TreeMap<Integer, Keyframe> keyframes) {
        return new Prefab.Track(keyframes.firstEntry().getValue().keyframeType(), null, 0, keyframes);
    }

    private static int duration(ImInt ticks) {
        return Math.max(1, ticks.get());
    }

    private static final class Orbit implements Template {
        private final float[] radius = {6};
        private final float[] elevation = {20};
        private final float[] turns = {1};
        private final ImInt ticks = new ImInt(200);
        private boolean clockwise;

        @Override public String id() { return "orbit"; }
        @Override public String nameKey() { return "vector3.prefab.template.orbit"; }

        @Override
        public boolean edit() {
            boolean changed = ImGui.dragFloat(I18n.get("vector3.prefab.radius"), radius, 0.1f, 0.1f, 1000);
            changed |= ImGui.dragFloat(I18n.get("vector3.prefab.elevation"), elevation, 0.5f, -89, 89);
            changed |= ImGui.dragFloat(I18n.get("vector3.prefab.turns"), turns, 0.05f, 0.05f, 50);
            changed |= ImGui.inputInt(I18n.get("vector3.prefab.duration"), ticks);
            if (ImGui.checkbox(I18n.get("vector3.prefab.clockwise"), clockwise)) {
                clockwise = !clockwise;
                changed = true;
            }
            return changed;
        }

        @Override
        public Prefab build() {
            int steps = Math.max(1, Math.round(turns[0] * 4));
            int duration = duration(ticks);
            TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
            for (int i = 0; i <= steps; i++) {
                float yaw = (clockwise ? -360 : 360) * turns[0] * i / steps;
                keyframes.put(Math.round((float) duration * i / steps), new CameraOrbitKeyframe(new Vector3d(),
                        Math.max(0.1f, radius[0]), yaw, elevation[0], InterpolationType.LINEAR));
            }
            return new Prefab(I18n.get(nameKey()), List.of(track(keyframes)));
        }
    }

    private static final class DollyZoom implements Template {
        private final float[] startDistance = {12};
        private final float[] endDistance = {4};
        private final float[] startFov = {40};
        private final float[] elevation = {0};
        private final ImInt ticks = new ImInt(100);
        private Target target = Target.at(Vec3.ZERO);

        @Override public String id() { return "dolly_zoom"; }
        @Override public String nameKey() { return "vector3.prefab.template.dolly_zoom"; }

        @Override
        public boolean edit() {
            Target edited = TargetEditor.edit(target, I18n.get("vector3.prefab.target_center"));
            boolean changed = !edited.equals(target);
            target = edited;
            changed |= ImGui.dragFloat(I18n.get("vector3.prefab.start_distance"), startDistance, 0.1f, 0.2f, 1000);
            changed |= ImGui.dragFloat(I18n.get("vector3.prefab.end_distance"), endDistance, 0.1f, 0.2f, 1000);
            changed |= ImGui.dragFloat(I18n.get("vector3.prefab.start_fov"), startFov, 0.5f, 1, 170);
            changed |= ImGui.dragFloat(I18n.get("vector3.prefab.elevation"), elevation, 0.5f, -89, 89);
            changed |= ImGui.inputInt(I18n.get("vector3.prefab.duration"), ticks);
            return changed;
        }

        @Override
        public Prefab build() {
            float d0 = Math.max(0.2f, startDistance[0]);
            float frame = ml.mypals.vectorthree.camera.dolly.DollyZoom.frameHeight(d0, startFov[0]);
            TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
            keyframes.put(0, new CustomKeyframe<>(DollyZoomKeyframeType.INSTANCE, new ml.mypals.vectorthree.camera.dolly.DollyZoom(
                    false, target, d0, frame, 180, elevation[0]), InterpolationType.EASE_IN_OUT));
            keyframes.put(duration(ticks), new CustomKeyframe<>(DollyZoomKeyframeType.INSTANCE, new ml.mypals.vectorthree.camera.dolly.DollyZoom(
                    true, target, Math.max(0.2f, endDistance[0]), frame, 180, elevation[0]), InterpolationType.EASE_IN_OUT));
            return new Prefab(I18n.get(nameKey()), List.of(track(keyframes)));
        }
    }
}
