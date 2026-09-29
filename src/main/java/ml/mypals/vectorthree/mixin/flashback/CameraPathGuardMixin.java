package ml.mypals.vectorthree.mixin.flashback;

import ml.mypals.vectorthree.core.Mod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.visuals.CameraPath;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

// Flashback reads the captured camera position at each nearby camera keyframe without checking it was captured; a
// keyframe that yields no position crashed the whole frame every frame. The path is skipped for that frame instead.
@Mixin(value = CameraPath.class, remap = false)
public class CameraPathGuardMixin {
    @Unique private static boolean vector3$reported;

    @WrapOperation(method = "renderCameraPath", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/visuals/CameraPath;buildCameraPath(Lcom/moulberry/flashback/state/EditorState;Lorg/joml/Vector3d;Lcom/moulberry/flashback/visuals/CameraPath$CameraPathArgs;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V"))
    private static void vector3$skipBrokenPath(EditorState editorState, Vector3d base, @Coerce Object args, BufferBuilder builder,
            Operation<Void> original) {
        try {
            original.call(editorState, base, args, builder);
        } catch (NullPointerException exception) {
            if (!vector3$reported) {
                vector3$reported = true;
                Mod.LOGGER.warn("Skipped Flashback's camera path: a camera keyframe gave no position ({})", args, exception);
            }
        }
    }
}
