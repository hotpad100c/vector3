package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImFont;
import imgui.moulberry90.ImFontAtlas;
import imgui.moulberry90.ImFontConfig;
import ml.mypals.vectorthree.flashback.VectorIcons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Merges vector3's icon font right after Flashback's Material icons (the second font it adds). The
 * atlas comes from that call: initFonts also runs on resource reloads, when no ImGui context is current.
 */
@Mixin(value = ReplayUI.class, remap = false)
public class ReplayUIFontMixin {
    @WrapOperation(method = "initFonts", at = @At(value = "INVOKE", ordinal = 1,
            target = "Limgui/moulberry90/ImFontAtlas;addFontFromMemoryTTF([BFLimgui/moulberry90/ImFontConfig;[S)Limgui/moulberry90/ImFont;"))
    private static ImFont vector3$mergeIcons(ImFontAtlas atlas, byte[] data, float size, ImFontConfig config,
            short[] ranges, Operation<ImFont> original) {
        ImFont font = original.call(atlas, data, size, config, ranges);
        VectorIcons.mergeInto(atlas);
        return font;
    }
}
