package ml.mypals.vectorthree.mixin.iris;

import ml.mypals.vectorthree.render.IrisBypassTarget;
import ml.mypals.vectorthree.render.TextGlow;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererIrisBypassMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void vector3$blitBypassTarget(CallbackInfo ci) {
        IrisBypassTarget.blitToMain();
        TextGlow.composite();
    }
}
