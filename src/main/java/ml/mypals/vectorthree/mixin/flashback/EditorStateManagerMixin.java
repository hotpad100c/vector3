package ml.mypals.vectorthree.mixin.flashback;

import ml.mypals.vectorthree.fb.Editors;
import com.moulberry.flashback.state.EditorStateManager;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EditorStateManager.class, remap = false)
public class EditorStateManagerMixin {
    @Inject(method = "reset", at = @At("TAIL"))
    private static void vector3$clearOnReplayClosed(CallbackInfo ci) {
        Editors.GIZMO_EDITOR.clear();
        Editors.ORBIT_GIZMO.clear();
        Editors.CAMERA_GIZMO.clear();
        Editors.LIGHT_GIZMO.clear();
        Editors.FOCUS_GIZMO.clear();
        Editors.PREFABS.clear();
        Editors.EDITOR_CAMERA.reset();
        ShapeTrackRegistry.clear();
        ml.mypals.vectorthree.fb.expression.ExpressionEditor.reset();
        ml.mypals.vectorthree.fb.expression.Globals.reset();
    }
}
