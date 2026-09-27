package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.moulberry.flashback.editor.ui.CustomImGuiImplB3D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// Flashback only skips draw commands whose clip rect is negative; a zero-sized one reaches enableScissor, which throws. Such commands draw nothing, so they're dropped.
@Mixin(value = CustomImGuiImplB3D.class, remap = false)
public class ImGuiEmptyClipMixin {
    @Unique private static boolean vector3$emptyClip;

    @WrapOperation(method = "renderDrawData", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;enableScissor(IIII)V"))
    private void vector3$skipEmptyScissor(RenderPass pass, int x, int y, int width, int height, Operation<Void> original) {
        vector3$emptyClip = width <= 0 || height <= 0;
        if (!vector3$emptyClip) original.call(pass, x, y, width, height);
    }

    @WrapOperation(method = "renderDrawData", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"))
    private void vector3$skipEmptyDraw(RenderPass pass, int baseVertex, int firstIndex, int indexCount, int instanceCount,
            int flags, Operation<Void> original) {
        if (vector3$emptyClip) {
            vector3$emptyClip = false;
            return;
        }
        original.call(pass, baseVertex, firstIndex, indexCount, instanceCount, flags);
    }
}
