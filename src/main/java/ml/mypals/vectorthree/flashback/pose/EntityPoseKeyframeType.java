package ml.mypals.vectorthree.flashback.pose;

import ml.mypals.vectorthree.shape.entity.ShapeEntities;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import ml.mypals.vectorthree.flashback.EntityPicker;
import ml.mypals.vectorthree.flashback.VectorIcons;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.flashback.channel.ChannelRows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Poses a living entity's model parts (head, arms, legs, ...); keyframes blend part by part. */
public final class EntityPoseKeyframeType extends CustomKeyframeType<EntityPose> {
    public static final EntityPoseKeyframeType INSTANCE = new EntityPoseKeyframeType();

    private EntityPoseKeyframeType() {
        super("vector3_entity_pose", "vector3.keyframe_type.entity_pose", EntityPose.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.POSE_TRACK, null); }

    @Override protected EntityPose createValue() { return new EntityPose(null, EntityPose.Mode.ABSOLUTE, Map.of()); }

    @Override protected EntityPose sanitize(EntityPose value) { return new EntityPose(value.entity(), value.mode(), value.parts()); }

    @Override protected EntityPose lerp(EntityPose from, EntityPose to, double amount) { return from.lerp(to, (float) amount); }

    @Override protected void apply(EntityPose value, KeyframeHandler handler) { EntityPoses.request(value); }

    @Override
    protected EntityPose edit(EntityPose value) {
        EntityPose edited = value;
        UUID picked = EntityPicker.combo(I18n.get("vector3.entity_pose.entity"), edited.entity(), true);
        if (!Objects.equals(picked, edited.entity())) edited = edited.withEntity(picked);

        String[] modes = {I18n.get("vector3.entity_pose.mode.absolute"), I18n.get("vector3.entity_pose.mode.additive")};
        ImInt mode = new ImInt(edited.mode().ordinal());
        if (ImGui.combo(I18n.get("vector3.entity_pose.mode"), mode, modes)) edited = edited.withMode(EntityPose.Mode.values()[mode.get()]);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.entity_pose.mode.tooltip"));
        if (edited.entity() == null) return edited;

        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = ShapeEntities.resolve(edited.entity());
        Map<String, EntityPose.Limb> rest = EntityPoses.restPose(entity);

        Map<String, EntityPose.Limb> snapshot = EntityPoses.takeSnapshot(edited.entity());
        if (snapshot != null) {
            // Captured values are absolute; in additive mode the captured rotations start from zero instead.
            boolean zeroed = edited.mode() == EntityPose.Mode.ADDITIVE;
            Map<String, EntityPose.Limb> captured = new LinkedHashMap<>();
            snapshot.forEach((name, limb) -> captured.put(name, zeroed ? EntityPose.Limb.NONE.withRotate(true) : limb));
            edited = edited.withParts(captured);
        }
        ImGui.beginDisabled(entity == null || EntityPoses.snapshotPending(edited.entity()));
        if (ImGui.button(I18n.get("vector3.entity_pose.capture"))) EntityPoses.requestSnapshot(edited.entity());
        ImGui.endDisabled();
        if (ImGui.isItemHovered(imgui.moulberry90.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(I18n.get(entity == null ? "vector3.entity_pose.not_loaded" : "vector3.entity_pose.capture.tooltip"));
        }

        // The entity's own parts when it is loaded; otherwise whatever the keyframe already has.
        Map<String, EntityPose.Limb> rows = new LinkedHashMap<>(rest);
        edited.parts().forEach(rows::put);
        if (rows.isEmpty()) {
            ImGui.textDisabled(I18n.get("vector3.entity_pose.no_parts"));
            return edited;
        }
        boolean additive = edited.mode() == EntityPose.Mode.ADDITIVE;
        for (Map.Entry<String, EntityPose.Limb> row : rows.entrySet()) {
            String name = row.getKey();
            EntityPose.Limb limb = edited.parts().getOrDefault(name, EntityPose.Limb.NONE);
            ImGui.pushID(name);
            // Picks the part for the viewport gizmo.
            if (ImGui.radioButton("##gizmo", name.equals(Vector3.POSE_GIZMO.part()))) Vector3.POSE_GIZMO.focus(name);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.entity_pose.gizmo_part"));
            ImGui.sameLine();
            if (limb.active()) ImGui.text(label(name));
            else ImGui.textDisabled(label(name));
            ImGui.indent();

            // Rotation and offset switch on separately, so another track can drive the channel left off here.
            if (ImGui.checkbox("##rotate", limb.rotate())) {
                // A part new to this keyframe starts at rest (or at zero when adding on top); otherwise the values stay.
                EntityPose.Limb atRest = row.getValue();
                EntityPose.Limb start = edited.parts().containsKey(name) ? limb
                        : additive ? limb.withRotation(0, 0, 0) : limb.withRotation(atRest.xRot(), atRest.yRot(), atRest.zRot());
                limb = start.withRotate(!limb.rotate());
                edited = edited.withPart(name, limb);
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.entity_pose.rotate.tooltip"));
            ImGui.sameLine();
            float[] rotation = {limb.xRot(), limb.yRot(), limb.zRot()};
            ImGui.beginDisabled(!limb.rotate());
            if (ImGui.dragFloat3(I18n.get("vector3.entity_pose.rotation"), rotation, 0.5f)) {
                limb = limb.withRotation(rotation[0], rotation[1], rotation[2]);
                edited = edited.withPart(name, limb);
            }
            ChannelRows.mark(name + ":rotate");
            ImGui.endDisabled();

            if (ImGui.checkbox("##move", limb.move())) {
                limb = limb.withMove(!limb.move());
                edited = edited.withPart(name, limb);
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.entity_pose.move.tooltip"));
            ImGui.sameLine();
            float[] offset = {limb.x(), limb.y(), limb.z()};
            ImGui.beginDisabled(!limb.move());
            if (ImGui.dragFloat3(I18n.get("vector3.entity_pose.offset"), offset, 0.05f)) {
                limb = limb.withOffset(offset[0], offset[1], offset[2]);
                edited = edited.withPart(name, limb);
            }
            ChannelRows.mark(name + ":move");
            ImGui.endDisabled();

            ImGui.unindent();
            ImGui.popID();
        }
        return edited;
    }

    // "right_hind_leg" -> "Right Hind Leg"; the common part names are translated.
    public static String label(String part) {
        String key = "vector3.entity_pose.part." + part;
        if (Language.getInstance().has(key)) return I18n.get(key);
        StringBuilder text = new StringBuilder();
        for (String word : part.split("_")) {
            if (word.isEmpty()) continue;
            if (!text.isEmpty()) text.append(' ');
            text.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return text.toString();
    }
}
