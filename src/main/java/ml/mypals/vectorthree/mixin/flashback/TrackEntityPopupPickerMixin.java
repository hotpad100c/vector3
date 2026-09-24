package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.keyframe.types.TrackEntityKeyframeType;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.flashback.EntityPicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The entity picker in Track Entity's add popup, whose body is the createPopup lambda. */
@Mixin(value = TrackEntityKeyframeType.class, remap = false)
public class TrackEntityPopupPickerMixin {
    @WrapOperation(method = "lambda$createPopup$0", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;inputText(Ljava/lang/String;Limgui/moulberry90/type/ImString;)Z"))
    private static boolean vector3$pickEntity(String label, ImString uuid, Operation<Boolean> original) {
        return EntityPicker.pickInto(label, uuid);
    }
}
