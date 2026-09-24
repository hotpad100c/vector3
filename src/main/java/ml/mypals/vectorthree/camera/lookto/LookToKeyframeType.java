package ml.mypals.vectorthree.camera.lookto;

import com.moulberry.flashback.combo_options.TrackingBodyPart;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.EntityPicker;
import ml.mypals.vectorthree.flashback.VectorIcons;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Forces the camera to look at a target between a keyframe and the next, until one ends the scope. */
public final class LookToKeyframeType extends CustomKeyframeType<LookTo> {
    public static final LookToKeyframeType INSTANCE = new LookToKeyframeType();
    private static final double PICK_DISTANCE = 256;

    private LookToKeyframeType() {
        super("vector3_look_to", "vector3.keyframe_type.look_to", LookTo.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.LOOK_TO_TRACK, "\ue8f4"); }

    @Override
    protected LookTo createValue() {
        return LookTo.at(crosshairTarget());
    }

    @Override
    protected LookTo edit(LookTo value) {
        LookTo edited = value;
        if (ImGui.checkbox(I18n.get("vector3.look_to.ends_scope"), edited.endsScope())) {
            edited = edited.withEndsScope(!edited.endsScope());
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.look_to.ends_scope.tooltip"));

        LookTo.Kind kind = ImGuiHelper.enumCombo(I18n.get("vector3.look_to.kind"), edited.kind());
        if (kind != edited.kind()) edited = edited.withKind(kind);

        switch (edited.kind()) {
            case ENTITY -> {
                UUID entity = EntityPicker.combo(I18n.get("vector3.look_to.entity"), edited.entity());
                if (!Objects.equals(entity, edited.entity())) edited = edited.withEntity(entity);
                TrackingBodyPart part = ImGuiHelper.enumCombo(I18n.get("flashback.body_part"), edited.bodyPart());
                if (part != edited.bodyPart()) edited = edited.withBodyPart(part);
            }
            case POSITION -> {
                float[] position = {(float) edited.x(), (float) edited.y(), (float) edited.z()};
                if (ImGui.dragFloat3(I18n.get("vector3.look_to.position"), position, 0.05f)) {
                    edited = edited.withPosition(position[0], position[1], position[2]);
                }
                if (ImGui.button(I18n.get("vector3.look_to.pick_crosshair"))) {
                    Vec3 target = crosshairTarget();
                    edited = edited.withPosition(target.x, target.y, target.z);
                }
            }
            case SHAPE -> {
                String shapeId = shapeCombo(edited.shapeId());
                if (!Objects.equals(shapeId, edited.shapeId())) edited = edited.withShapeId(shapeId);
            }
        }
        return edited;
    }

    // Only reached through createChange, e.g. Flashback applying a single keyframe; scopes use customKeyframeChange.
    @Override
    protected void apply(LookTo value, KeyframeHandler handler) {
        LookToCamera.request(value, value, 0);
    }

    @Override
    protected LookTo sanitize(LookTo value) {
        return new LookTo(value.endsScope(), value.kind() == null ? LookTo.Kind.POSITION : value.kind(), value.entity(),
                value.bodyPart() == null ? TrackingBodyPart.HEAD : value.bodyPart(),
                value.x(), value.y(), value.z(), value.shapeId());
    }

    @Override public boolean hasCustomKeyframeChangeCalculation() { return true; }

    @Override
    public @Nullable KeyframeChange customKeyframeChange(TreeMap<Integer, Keyframe> keyframes, float tick) {
        Map.Entry<Integer, Keyframe> start = keyframes.floorEntry((int) Math.floor(tick));
        if (start == null) return null;
        LookTo from = valueOf(start.getValue());
        if (from.endsScope()) return null;
        Map.Entry<Integer, Keyframe> end = keyframes.higherEntry(start.getKey());
        if (end == null) return action(handler -> LookToCamera.request(from, from, 0));
        LookTo to = valueOf(end.getValue());
        float progress = Math.clamp((tick - start.getKey()) / (end.getKey() - start.getKey()), 0, 1);
        float amount = SidedInterpolationType.interpolate(start.getValue().interpolationType().rightSide,
                end.getValue().interpolationType().leftSide, progress);
        return action(handler -> LookToCamera.request(from, to, amount));
    }

    /** Draws the scope as a bar to the next keyframe, and scope ends as hollow squares. */
    @Override
    protected boolean drawOnTimeline(CustomKeyframe<LookTo> keyframe, ImDrawList drawList, int size, float x, float y,
            int colour, float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        if (!keyframe.value.endsScope()) {
            Integer next = keyframes.higherKey(tick);
            float endX = next == null ? maxX : x + (next - tick) / ticksPerPixel;
            float left = Math.max(x, minX);
            float right = Math.min(endX, maxX);
            if (right > left) drawList.addLine(left, y, right, y, (colour & 0x00FFFFFF) | 0x99000000, size * 0.5f);
            return false;
        }
        drawList.addRect(x - size, y - size, x + size, y + size, colour, 0, 0, 2);
        return true;
    }

    private static @Nullable String shapeCombo(@Nullable String current) {
        String preview = current == null ? I18n.get("vector3.entity_picker.none") : ShapeTrackRegistry.displayName(current);
        String result = current;
        if (ImGui.beginCombo(I18n.get("vector3.look_to.shape"), preview)) {
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
    private static Vec3 crosshairTarget() {
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
