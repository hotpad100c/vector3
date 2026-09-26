package ml.mypals.vectorthree.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ml.mypals.vectorthree.text.VanillaText;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Each of the eight outline copies sits one shadow offset away; a ring of a wider outline sits further out.
@Mixin(Font.class)
public class VanillaTextOutlineMixin {
    @ModifyExpressionValue(method = "lambda$prepare8xTextOutline$0", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/font/GlyphInfo;getShadowOffset()F"))
    private float vector3$ringOffset(float offset) {
        return offset * VanillaText.outlineScale;
    }
}
