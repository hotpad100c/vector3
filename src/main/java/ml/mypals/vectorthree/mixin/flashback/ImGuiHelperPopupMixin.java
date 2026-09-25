package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ImGuiHelper.class, remap = false)
public class ImGuiHelperPopupMixin {
    @ModifyArg(method = "beginPopup(Ljava/lang/String;I)Z", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;beginPopup(Ljava/lang/String;I)Z"), index = 1)
    private static int vector3$movablePopup(int flags) {
        return flags & ~ImGuiWindowFlags.NoMove;//Disable flashback's pin,
    }
}
