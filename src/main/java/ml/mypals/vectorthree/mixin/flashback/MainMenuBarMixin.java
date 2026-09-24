package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.ui.windows.MainMenuBar;
import ml.mypals.vectorthree.flashback.ShapeManagerWindow;
import ml.mypals.vectorthree.prefab.PrefabBasketWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a Shape Manager toggle next to Flashback's own window toggles, just before "Hide Replay UI". */
@Mixin(value = MainMenuBar.class, remap = false)
public class MainMenuBarMixin {
    @Inject(method = "renderInner", at = @At(value = "CONSTANT", args = "stringValue=flashback.hide_replay_ui"))
    private static void vector3$addShapeManagerToggle(CallbackInfo ci) {
        ShapeManagerWindow.renderMenuItem();
        PrefabBasketWindow.renderMenuItem();
    }
}
