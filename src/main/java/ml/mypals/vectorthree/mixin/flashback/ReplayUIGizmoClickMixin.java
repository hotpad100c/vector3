package ml.mypals.vectorthree.mixin.flashback;

import ml.mypals.vectorthree.fb.Editors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.editor.ui.ReplayUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Right-clicking a vector3 gizmo (often drawn over an entity, e.g. a pose marker) mustn't also open Flashback's
// entity popup.
@Mixin(value = ReplayUI.class, remap = false)
public class ReplayUIGizmoClickMixin {
    @WrapOperation(method = "handleBasicInputs", at = @At(value = "INVOKE", ordinal = 0,
            target = "Limgui/moulberry90/ImGui;isMouseClicked(I)Z"))
    private static boolean vector3$gizmoTakesRightClick(int button, Operation<Boolean> original) {
        if (button == 1 && (Editors.POSE_GIZMO.isHovering() || Editors.CAMERA_GIZMO.isHovering()
                || Editors.ORBIT_GIZMO.isHovering() || Editors.GIZMO_EDITOR.isHovering()
                || Editors.PREFABS.isHovering() || ml.mypals.vectorthree.fb.camera.MotionPaths.isHovering())) {
            return false;
        }
        return original.call(button);
    }
}
