package ml.mypals.vectorthree.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.text.VanillaText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextFeatureRenderer.class)
public class VanillaTextRingsMixin {
    @WrapOperation(method = "renderText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepare8xTextOutline(Lnet/minecraft/util/FormattedCharSequence;FFI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static Font.PreparedText vector3$wideOutline(Font font, FormattedCharSequence text, float x, float y, int color,
            Operation<Font.PreparedText> original) {
        return VanillaText.rings(() -> original.call(font, text, x, y, color));
    }
}
