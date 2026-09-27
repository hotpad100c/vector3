package ml.mypals.vectorthree.mixin.flashback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.moulberry.flashback.editor.keybinds.Keybind", remap = false)
public interface KeybindAccessor {
    @Accessor("description") String vector3$description();
    @Invoker("longKeyIdentifier") String vector3$keys();
    @Invoker("isPressed") boolean vector3$isPressed(boolean repeat);
}
