package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.ui.windows.PreferencesWindow;
import ml.mypals.vectorthree.fb.shape.ShapeManagerWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PreferencesWindow.class, remap = false)
public class PreferencesWindowMixin {
    @Inject(method = "render", at = @At(value = "CONSTANT", args = "stringValue=flashback.advanced"))
    private static void vector3$settings(CallbackInfo ci) {
        ShapeManagerWindow.renderPreferences();
    }
}
