package ml.mypals.vectorthree.mixin;

import ml.mypals.vectorthree.text.VanillaText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderTypes.class)
public class GlyphRenderTypesGlowMixin {
    @Inject(method = "select", at = @At("HEAD"), cancellable = true)
    private void vector3$glowType(Font.DisplayMode mode, CallbackInfoReturnable<RenderType> cir) {
        if (VanillaText.glow == null) return;
        RenderType glow = VanillaText.glowType((GlyphRenderTypes) (Object) this, mode);
        if (glow != null) cir.setReturnValue(glow);
    }
}
