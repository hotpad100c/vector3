package ml.mypals.vectorthree.camera.target;

import com.moulberry.flashback.combo_options.TrackingBodyPart;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.EntityPicker;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.Objects;
import java.util.UUID;

/** The editor for a {@link Target}: entity picker with body part, position with crosshair pick, or shape. */
public final class TargetEditor {
    private static final double PICK_DISTANCE = 256;

    private TargetEditor() {}

    public static Target edit(Target target) {
        return edit(target, null);
    }

    /**
     * @param fixedPositionLabel when set, a position target isn't editable and shows this instead, e.g.
     *                           for a prefab whose position target is its placement center
     */
    public static Target edit(Target target, @Nullable String fixedPositionLabel) {
        Target edited = target;
        Target.Kind kind = ImGuiHelper.enumCombo(I18n.get("vector3.target"), edited.kind());
        if (kind != edited.kind()) edited = edited.withKind(kind);
        switch (edited.kind()) {
            case ENTITY -> {
                UUID entity = EntityPicker.combo(I18n.get("vector3.target.entity"), edited.entity());
                if (!Objects.equals(entity, edited.entity())) edited = edited.withEntity(entity);
                TrackingBodyPart part = ImGuiHelper.enumCombo(I18n.get("flashback.body_part"), edited.bodyPart());
                if (part != edited.bodyPart()) edited = edited.withBodyPart(part);
            }
            case POSITION -> {
                if (fixedPositionLabel != null) {
                    ImGui.textDisabled(fixedPositionLabel);
                    break;
                }
                float[] position = {(float) edited.x(), (float) edited.y(), (float) edited.z()};
                if (ImGui.dragFloat3(I18n.get("vector3.target.position"), position, 0.05f)) {
                    edited = edited.withPosition(position[0], position[1], position[2]);
                }
                if (ImGui.button(I18n.get("vector3.target.pick_crosshair"))) {
                    Vec3 hit = crosshairTarget();
                    edited = edited.withPosition(hit.x, hit.y, hit.z);
                }
            }
            case SHAPE -> {
                String shapeId = shapeCombo(edited.shapeId());
                if (!Objects.equals(shapeId, edited.shapeId())) edited = edited.withShapeId(shapeId);
            }
        }
        return edited;
    }

    private static @Nullable String shapeCombo(@Nullable String current) {
        String preview = current == null ? I18n.get("vector3.entity_picker.none") : ShapeTrackRegistry.displayName(current);
        String result = current;
        if (ImGui.beginCombo(I18n.get("vector3.target.shape"), preview)) {
            for (String shapeId : ShapeTrackRegistry.shapeIds()) {
                if (ImGui.selectable(ShapeTrackRegistry.displayName(shapeId) + "###" + shapeId, shapeId.equals(current))) {
                    result = shapeId;
                }
            }
            ImGui.endCombo();
        }
        return result;
    }

    /** The block under the camera's crosshair, or a point in front of the camera when there is none. */
    public static Vec3 crosshairTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vector3fc forward = camera.forwardVector();
        Vec3 from = camera.position();
        Vec3 to = from.add(forward.x() * PICK_DISTANCE, forward.y() * PICK_DISTANCE, forward.z() * PICK_DISTANCE);
        if (minecraft.level != null && minecraft.player != null) {
            HitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, minecraft.player));
            if (hit.getType() != HitResult.Type.MISS) return hit.getLocation();
        }
        return from.add(forward.x() * 5, forward.y() * 5, forward.z() * 5);
    }
}
