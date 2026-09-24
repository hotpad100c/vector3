package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.state.EditorStateManager;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flashback calls {@code EditorStateManager.reset()} (from its own {@code MixinMinecraft.tick()})
 * exactly when {@code Flashback.getReplayServer()} transitions from a live replay to none — i.e.
 * when a replay is closed. That happens without ever firing Fabric's
 * {@code ClientPlayConnectionEvents.DISCONNECT} (the underlying connection/world can persist
 * across a replay switch), which is why vector3's own disconnect-based cleanup in
 * {@link Vector3#onInitializeClient()} never ran and left one replay's shapes visible in the next.
 */
@Mixin(value = EditorStateManager.class, remap = false)
public class EditorStateManagerMixin {
    @Inject(method = "reset", at = @At("TAIL"))
    private static void vector3$clearOnReplayClosed(CallbackInfo ci) {
        Vector3.GIZMO_EDITOR.clear();
        Vector3.ORBIT_GIZMO.clear();
        Vector3.EDITOR_CAMERA.reset();
        ShapeTrackRegistry.clear();
    }
}
