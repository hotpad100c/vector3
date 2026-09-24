package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.state.EditorStateManager;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EditorStateManager.class, remap = false)
public class EditorStateManagerMixin {
    @Inject(method = "reset", at = @At("TAIL"))
    private static void vector3$clearOnReplayClosed(CallbackInfo ci) {
        Vector3.GIZMO_EDITOR.clear();
        Vector3.ORBIT_GIZMO.clear();
        Vector3.PREFABS.clear();
        Vector3.EDITOR_CAMERA.reset();
        ShapeTrackRegistry.clear();
    }
}
