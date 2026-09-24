package ml.mypals.vectorthree.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.BlockShape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.EntityShape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.ItemShape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Text always goes into IrisBypassTarget like the other shapes. Item/block/entity shapes opt in per
 * shape (ShapeState#bypassShaders, handed over through Shape#customData): drawn unshaded in the bypass
 * pass they depth-test against the other shapes in the same buffer; otherwise the shader pack shades them
 * in the main target and the bypass target folds in their depth for anything drawn after.
 */
@Mixin(value = {ItemShape.class, BlockShape.class, EntityShape.class, TextShape.class}, remap = false)
public class BuiltInShapeIrisBypassMixin {
    @WrapOperation(method = "drawInternal", at = @At(value = "INVOKE",
            target = "Lml/mypals/ryansrenderingkit/utils/Helpers;renderFeatures(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void vector3$renderIntoBypassTarget(Minecraft mc, SubmitNodeStorage submits, Operation<Void> original) {
        Shape self = (Shape) (Object) this;
        if (self instanceof TextShape || Boolean.TRUE.equals(self.customData.get(IrisBypassTarget.SHAPE_FLAG))) {
            IrisBypassTarget.renderFeatures(() -> original.call(mc, submits));
        } else {
            original.call(mc, submits);
            IrisBypassTarget.markMainDepthChanged();
        }
    }
}
