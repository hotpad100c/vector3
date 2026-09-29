package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.state.EditorScene;
import ml.mypals.vectorthree.expression.lang.ExprError;
import ml.mypals.vectorthree.expression.lang.Scope;
import ml.mypals.vectorthree.expression.lang.Value;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The scene's global variables: evaluated when read, once per pass, each in a scope of its own. */
public final class Globals {
    /** Implemented on EditorScene; saved with it by Flashback's reflective Gson. */
    public interface Holder {
        GlobalVariable @Nullable [] vector3$globals();

        void vector3$setGlobals(GlobalVariable @Nullable [] globals);
    }

    /** The last evaluation of a global: its value, or the error. */
    public record Status(@Nullable Value value, @Nullable String error, int position, Map<String, Value> lets) {}

    private static final Map<String, Status> STATUS = new HashMap<>();
    private static final Set<String> EVALUATING = new HashSet<>();

    private Globals() {}

    public static List<GlobalVariable> of(@Nullable EditorScene scene) {
        GlobalVariable[] globals = scene == null ? null : ((Holder) scene).vector3$globals();
        return globals == null ? List.of() : List.of(globals);
    }

    public static void set(EditorScene scene, List<GlobalVariable> globals) {
        ((Holder) scene).vector3$setGlobals(globals.isEmpty() ? null : globals.toArray(GlobalVariable[]::new));
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (GlobalVariable global : of(EvalContext.currentScene())) names.add(global.name());
        return names;
    }

    /** Forgets the last results, when another replay opens. */
    public static void reset() {
        STATUS.clear();
    }

    public static @Nullable Status status(String name) {
        return STATUS.get(name);
    }

    static Value get(String name, float tick) {
        GlobalVariable global = null;
        for (GlobalVariable candidate : of(EvalContext.currentScene())) {
            if (candidate.name().equals(name)) global = candidate;
        }
        if (global == null) throw new ExprError("no global." + name + " (defined: " + String.join(", ", names()) + ")");
        EvalContext context = EvalContext.current();
        boolean memo = context != null && context.tick == tick;
        if (memo && context.globals.containsKey(name)) return context.globals.get(name);
        if (!EVALUATING.add(name)) throw new ExprError("global." + name + " refers back to itself");
        Map<String, Value> lets = new HashMap<>();
        try {
            Value value = ExpressionRuntime.program(global.source(), false).eval(new GlobalScope(tick, lets));
            STATUS.put(name, new Status(value, null, -1, lets));
            if (memo) context.globals.put(name, value);
            return value;
        } catch (ExprError error) {
            STATUS.put(name, new Status(null, error.getMessage(), error.position(), lets));
            throw new ExprError("global." + name + ": " + error.getMessage());
        } finally {
            EVALUATING.remove(name);
        }
    }

    /** Evaluates a global for the editor, outside a keyframe pass; errors land in its status. */
    public static void preview(String name, float tick) {
        try {
            get(name, tick);
        } catch (ExprError ignored) {
        }
    }

    public static boolean validName(String name) {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private record GlobalScope(float at, Map<String, Value> lets) implements Scope {
        @Override public double tick() { return at; }
        @Override public Value value() { throw new ExprError("value is not available in a global"); }
        @Override public Value track(String name) { return References.track(name, at); }
        @Override public Value entity(String id) { return References.entity(id); }
        @Override public Value camera() { return References.camera(); }
        @Override public Value self() { throw new ExprError("self is not available in a global"); }
        @Override public Value global(String name) { return Globals.get(name, at); }
        @Override public List<String> globalNames() { return names(); }
        @Override public void let(String name, Value value) { lets.put(name, value); }
    }
}
