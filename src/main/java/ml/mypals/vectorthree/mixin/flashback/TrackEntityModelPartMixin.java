package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import ml.mypals.vectorthree.flashback.pose.ModelPartCombo;
import ml.mypals.vectorthree.flashback.pose.ModelPartHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

// Track Entity keyframes can anchor the camera on one part of the target's model; see TrackEntityChangeModelPartMixin.
@Mixin(value = TrackEntityKeyframe.class, remap = false)
public class TrackEntityModelPartMixin implements ModelPartHolder {
    @Unique private String vector3$modelPart;
    @Unique private boolean vector3$followPartRotation;

    @Override public String vector3$modelPart() { return vector3$modelPart; }
    @Override public void vector3$setModelPart(String part) { vector3$modelPart = part; }
    @Override public boolean vector3$followPartRotation() { return vector3$followPartRotation; }
    @Override public void vector3$setFollowPartRotation(boolean follow) { vector3$followPartRotation = follow; }

    @Inject(method = {"copy", "createChange", "createSmoothInterpolatedChange", "createHermiteInterpolatedChange"},
            at = @At("RETURN"))
    private void vector3$carryModelPart(CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() instanceof ModelPartHolder holder && holder.vector3$modelPart() == null) {
            ModelPartHolder.copy(this, holder);
        }
    }

    // Offsets and angles drag with the mouse instead of being typed (ctrl-click or double-click still types).
    @Redirect(method = "renderEditKeyframe", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;inputFloat(Ljava/lang/String;[F)Z"))
    private boolean vector3$dragFloat(String label, float[] values) {
        return switch (values.length) {
            case 1 -> ImGui.dragFloat(label, values, 0.5f);
            case 3 -> ImGui.dragFloat3(label, values, 0.02f);
            default -> com.moulberry.flashback.editor.ui.ImGuiHelper.inputFloat(label, values);
        };
    }

    @Inject(method = "renderEditKeyframe", at = @At("TAIL"))
    private void vector3$modelPartCombo(Consumer<Consumer<Keyframe>> update, CallbackInfo ci) {
        UUID target = ((TrackEntityKeyframe) (Object) this).target;
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = target == null || minecraft.level == null ? null : minecraft.level.getEntity(target);
        String picked = ModelPartCombo.render(I18n.get("vector3.mount.model_part"), entity, vector3$modelPart);
        if (!Objects.equals(picked, vector3$modelPart)) {
            update.accept(keyframe -> ((ModelPartHolder) keyframe).vector3$setModelPart(picked));
        }
        if (vector3$modelPart == null) return;
        ImBoolean follow = new ImBoolean(vector3$followPartRotation);
        if (ImGui.checkbox(I18n.get("vector3.track_entity.follow_part_rotation"), follow)) {
            boolean value = follow.get();
            update.accept(keyframe -> ((ModelPartHolder) keyframe).vector3$setFollowPartRotation(value));
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.track_entity.follow_part_rotation.tooltip"));
    }
}
