package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.flashback.EntityPicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Track Entity's editor only takes a typed UUID; show the entity picker instead. Flashback still parses
 * and validates the UUID the picker writes into the field. {@link TrackEntityPopupPickerMixin} does the
 * same for the add popup.
 */
@Mixin(value = TrackEntityKeyframe.class, remap = false)
public class TrackEntityPickerMixin {
    @WrapOperation(method = "renderEditKeyframe", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;inputText(Ljava/lang/String;Limgui/moulberry90/type/ImString;)Z"))
    private boolean vector3$pickEntity(String label, ImString uuid, Operation<Boolean> original) {
        return EntityPicker.pickInto(label, uuid);
    }
}
