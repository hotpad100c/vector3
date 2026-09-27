package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.keybinds.Keybinds;
import ml.mypals.vectorthree.flashback.VectorKeybinds;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = Keybinds.class, remap = false)
public class KeybindsRegistryMixin {
    @Shadow @Final @Mutable private static List<Object> KEYBINDS;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void vector3$registerKeybinds(CallbackInfo ci) {
        List<Object> all = new ArrayList<>(KEYBINDS);
        all.addAll(VectorKeybinds.all());
        KEYBINDS = all;
    }
}
