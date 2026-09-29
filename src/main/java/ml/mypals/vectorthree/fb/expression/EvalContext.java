package ml.mypals.vectorthree.fb.expression;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.core.expression.lang.Value;
import ml.mypals.vectorthree.mixin.flashback.EditorStateAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** One pass of applying keyframes: the scene expressions can look tracks up in, and what they already read. */
public final class EvalContext {
    private static final ArrayDeque<EvalContext> STACK = new ArrayDeque<>();

    final float tick;
    @Nullable EditorScene scene;
    final Map<KeyframeTrack, References.Snapshot> captures = new IdentityHashMap<>();
    final Map<String, KeyframeTrack> tracks = new HashMap<>();
    final Map<String, Value> globals = new HashMap<>();

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
        EvalContext current = STACK.peek();
        if (current != null) current.scene = scene;
    }

    static @Nullable EvalContext current() {
        return STACK.peek();
    }

    /** The scene being applied, else the open replay's (the export's while exporting); null with no replay open. */
    static @Nullable EditorScene currentScene() {
        EvalContext current = STACK.peek();
        if (current != null && current.scene != null) return current.scene;
        EditorState state = EditorStateManager.getCurrent();
        return state == null ? null : ((EditorStateAccessor) state).vector3$currentScene();
    }
}
