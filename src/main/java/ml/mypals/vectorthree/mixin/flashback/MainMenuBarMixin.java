package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.windows.MainMenuBar;
import com.moulberry.flashback.editor.ui.windows.WindowType;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.PropertiesWindow;
import ml.mypals.vectorthree.flashback.ShapeManagerWindow;
import ml.mypals.vectorthree.prefab.PrefabBasketWindow;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

// Flashback's row of window toggles becomes one Window menu, which also holds vector3's windows.
@Mixin(value = MainMenuBar.class, remap = false)
public class MainMenuBarMixin {
    @Inject(method = "renderInner", at = @At(value = "CONSTANT", args = "stringValue=flashback.player_list"))
    private static void vector3$windowMenu(CallbackInfo ci) {
        if (!ImGui.beginMenu(I18n.get("vector3.menu.window"))) return;
        for (WindowType type : WindowType.values()) {
            String id = type.name().toLowerCase(Locale.ROOT);
            boolean open = Flashback.getConfig().internal.openedWindows.contains(id);
            if (ImGui.menuItem(I18n.get("flashback." + id), "", open)) type.toggle();
        }
        ImGui.separator();
        PropertiesWindow.renderMenuItem();
        ShapeManagerWindow.renderMenuItem();
        PrefabBasketWindow.renderMenuItem();
        ImGui.endMenu();
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;menuItem(Ljava/lang/String;)Z"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=flashback.player_list"),
                    to = @At(value = "CONSTANT", args = "stringValue=flashback.hide_replay_ui")))
    private static boolean vector3$hideWindowToggle(String label) {
        return false;
    }
}
