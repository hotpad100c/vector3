package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.keybinds.Keybind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.moulberry.flashback.editor.keybinds.Keybind", remap = false)
public interface KeybindFactory {
    @Invoker("<init>")
    static Keybind vector3$create(String description, int key, boolean shift, boolean ctrl, boolean alt, boolean superMod) {
        throw new AssertionError();
    }
}
