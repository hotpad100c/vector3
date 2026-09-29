package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** One pass of applying keyframes: the scene expressions can look tracks up in, and what they already read. */
public final class EvalContext {
    private static final ArrayDeque<EvalContext> STACK = new ArrayDeque<>();
    private static WeakReference<EditorScene> lastScene = new WeakReference<>(null);

    final float tick;
    @Nullable EditorScene scene;
    final Map<KeyframeTrack, Map<String, Object>> captures = new IdentityHashMap<>();
    final Map<String, KeyframeTrack> tracks = new HashMap<>();

    private EvalContext(float tick) {
        this.tick = tick;
    }

    public static void begin(float tick) {
        STACK.push(new EvalContext(tick));
    }

    public static void end() {
        STACK.poll();
    }

    public static void scene(EditorScene scene) {
        lastScene = new WeakReference<>(scene);
        EvalContext current = STACK.peek();
        if (current != null) current.scene = scene;
    }

    static @Nullable EvalContext current() {
        return STACK.peek();
    }

    static @Nullable EditorScene currentScene() {
        EvalContext current = STACK.peek();
        return current != null && current.scene != null ? current.scene : lastScene.get();
    }
}
