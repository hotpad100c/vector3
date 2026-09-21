package ml.mypals.vectorthree.mixin;

import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import ml.mypals.vectorthree.shape.TextFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextShape.class, remap = false)
public class TextShapeMixin {
    @Inject(method = "getRenderMessages", at = @At("HEAD"), cancellable = true)
    private void vector3$parseStyledText(CallbackInfoReturnable<FormattedCharSequence[]> cir) {
        cir.setReturnValue(TextFormatting.format(((TextShape) (Object) this).contents));
    }

    @Inject(method = "enabled", at = @At("HEAD"), cancellable = true)
    private void vector3$honorVisibility(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(((TextShape) (Object) this).enabled);
    }

    @Redirect(method = "drawInternal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int vector3$measureStyledText(Font font, String text) {
        return font.width(TextFormatting.formatLine(text));
    }
}
