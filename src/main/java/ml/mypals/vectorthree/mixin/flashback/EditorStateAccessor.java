package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// getCurrentScene(stamp) needs a lock, which some callers already hold (StampedLock isn't reentrant).
@Mixin(value = EditorState.class, remap = false)
public interface EditorStateAccessor {
    @Invoker("currentScene")
    EditorScene vector3$currentScene();
}
