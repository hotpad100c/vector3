package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.ui.ReplayUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ReplayUI.class, remap = false)
public interface ReplayUIAccessor {
    @Accessor("isFrameHovered")
    static boolean vector3$isFrameHovered() {
        throw new AssertionError();
    }
}
