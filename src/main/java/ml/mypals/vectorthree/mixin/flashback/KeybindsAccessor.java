package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.keybinds.Keybinds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

@Mixin(value = Keybinds.class, remap = false)
public interface KeybindsAccessor {
    @Accessor("keybindsForKey")
    static Map<Integer, Set<Object>> vector3$keybindsForKey() {
        throw new AssertionError();
    }
}
