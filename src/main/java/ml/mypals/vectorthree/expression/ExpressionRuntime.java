package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import com.mojang.logging.LogUtils;
import ml.mypals.vectorthree.expression.lang.ExprError;
import ml.mypals.vectorthree.expression.lang.Program;
import ml.mypals.vectorthree.expression.lang.Scope;
import ml.mypals.vectorthree.expression.lang.Value;
import ml.mypals.vectorthree.flashback.TrackManagement;
import ml.mypals.vectorthree.flashback.loop.TrackRepeat;
import ml.mypals.vectorthree.multiedit.MultiEditSession;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Applies a track's expressions to what it evaluates to. Nothing here knows any keyframe type: the evaluated value is
 * rebuilt into a keyframe, and its own editor is replayed with the expressions' results (see MultiEditSession), in a
 * private ImGui context. Types whose value can't be rebuilt get the results replayed into copies of the keyframes
 * around the tick instead, which then interpolate to them.
 */
public final class ExpressionRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<KeyframeTrack> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int WINDOW = 2;
    private static int evaluating;
    private static References.@Nullable SelfRef self;

    private record Compiled(@Nullable Program program, @Nullable ExprError error) {}

    private static final Map<String, Compiled> PROGRAMS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Compiled> eldest) {
            return size() > 512;
        }
    };

    private ExpressionRuntime() {}

    /** True while an editor runs for an expression, so it must not re-apply keyframes or touch the world. */
    public static boolean evaluating() {
        return evaluating > 0;
    }

    public static @Nullable KeyframeChange apply(KeyframeTrack track, float tick, @Nullable RealTimeMapping mapping,
            Supplier<KeyframeChange> original) {
        ExpressionBinding[] bindings = ((ExpressionBindings.Holder) track).vector3$expressions();
        // Per-channel evaluation calls back into the same track; only the outermost call applies expressions.
        if (bindings == null || bindings.length == 0 || ACTIVE.contains(track)) return original.get();
        ACTIVE.add(track);
        try {
            KeyframeChange change = original.get();
            // A driven track holds its first keyframe before it, so the expressions still run there.
            if (change == null && !track.keyframesByTick.isEmpty() && tick < track.keyframesByTick.firstKey()) {
                change = track.keyframesByTick.firstEntry().getValue().createChange();
            }
            if (change == null) return null;
            // Past the last keyframe Flashback evaluates the track at that keyframe's tick; time goes on regardless.
            EvalContext context = EvalContext.current();
            float time = context != null ? context.tick : tick;
            Map<String, UnaryOperator<Object>> operations = operations(track, time, bindings);
            if (operations.isEmpty()) return change;
            try {
                Keyframe scratch = KeyframeRebuild.of(track, change);
                if (scratch != null) return drive(track, scratch, operations, bindings) ? scratch.createChange() : change;
                return window(track, tick, mapping, operations, bindings, change);
            } catch (RuntimeException error) {
                LOGGER.warn("Expressions of a {} track failed", track.keyframeType.id(), error);
                return change;
            }
        } finally {
            ACTIVE.remove(track);
        }
    }

    static boolean active(KeyframeTrack track) {
        return ACTIVE.contains(track);
    }

    /** What the track evaluates to at {@code tick} with its expressions left out. */
    static @Nullable KeyframeChange withoutExpressions(KeyframeTrack track, float tick) {
        if (!ACTIVE.add(track)) return track.createKeyframeChange(tick, null);
        try {
            return track.createKeyframeChange(tick, null);
        } finally {
            ACTIVE.remove(track);
        }
    }

    public static @Nullable ExprError check(String source, boolean template) {
        return compile(source, template).error();
    }

    private static Map<String, UnaryOperator<Object>> operations(KeyframeTrack track, float tick, ExpressionBinding[] bindings) {
        Map<String, List<ExpressionBinding>> byWidget = new LinkedHashMap<>();
        for (ExpressionBinding binding : bindings) {
            if (!binding.enabled()) continue;
            for (String label : WidgetKeys.labels(binding.widget())) {
                byWidget.computeIfAbsent(label, key -> new ArrayList<>()).add(binding);
            }
        }
        Map<String, UnaryOperator<Object>> operations = new LinkedHashMap<>();
        byWidget.forEach((label, list) -> operations.put(label, before -> compute(track, tick, list, before)));
        return operations;
    }

    private static boolean drive(KeyframeTrack track, Keyframe keyframe, Map<String, UnaryOperator<Object>> operations,
            ExpressionBinding[] bindings) {
        Set<String> hits = new HashSet<>();
        References.SelfRef outer = self;
        self = new References.SelfRef(keyframe.copy(), track.keyframeType);
        boolean ran;
        try {
            ran = runEditor(() -> MultiEditSession.replayComputed(operations, hits,
                    () -> keyframe.renderEditKeyframe(edit -> edit.accept(keyframe))));
        } finally {
            self = outer;
        }
        for (ExpressionBinding binding : bindings) {
            if (binding.enabled() && WidgetKeys.labels(binding.widget()).stream().noneMatch(hits::contains)) {
                ExpressionBindings.report(track, binding, ExpressionBindings.Status.MISSING);
            }
        }
        return ran;
    }

    private static KeyframeChange window(KeyframeTrack track, float tick, @Nullable RealTimeMapping mapping,
            Map<String, UnaryOperator<Object>> operations, ExpressionBinding[] bindings, KeyframeChange change) {
        TreeMap<Integer, Keyframe> keyframes = track.keyframesByTick;
        if (keyframes.isEmpty()) return change;
        float at = TrackRepeat.of(track).remap(keyframes, tick);
        Integer below = keyframes.floorKey((int) Math.floor(at));
        Integer above = keyframes.higherKey((int) Math.floor(at));
        TreeMap<Integer, Keyframe> window = new TreeMap<>();
        for (int i = 0; below != null && i < WINDOW; i++, below = keyframes.lowerKey(below)) {
            window.put(below, keyframes.get(below).copy());
        }
        for (int i = 0; above != null && i < WINDOW; i++, above = keyframes.higherKey(above)) {
            window.put(above, keyframes.get(above).copy());
        }
        for (Keyframe copy : window.values()) {
            if (!drive(track, copy, operations, bindings)) return change;
        }
        KeyframeTrack driven = new KeyframeTrack(track.keyframeType);
        driven.keyframesByTick = window;
        driven.enabled = true;
        ((TrackManagement.Holder) driven).vector3$setSolo(((TrackManagement.Holder) track).vector3$solo());
        KeyframeChange result = driven.createKeyframeChange(at, mapping);
        return result != null ? result : change;
    }

    /** Runs an editor pass for an expression in the private ImGui context. */
    static boolean runEditor(Runnable pass) {
        return HeadlessImGui.run(() -> {
            evaluating++;
            try {
                pass.run();
            } finally {
                evaluating--;
            }
        });
    }

    private static @Nullable Object compute(KeyframeTrack track, float tick, List<ExpressionBinding> bindings, Object before) {
        Object after = before instanceof float[] floats ? floats.clone() : before instanceof int[] ints ? ints.clone() : before;
        boolean wrote = false;
        for (ExpressionBinding binding : bindings) {
            Compiled compiled = compile(binding.source(), before instanceof String);
            if (compiled.error() != null) {
                report(track, binding, compiled.error(), Map.of());
                continue;
            }
            Map<String, Value> lets = new java.util.HashMap<>();
            Value own = own(before, binding.component());
            try {
                Value result = compiled.program().eval(new TrackScope(tick, own, self, lets));
                after = write(after, binding.component(), result);
                ExpressionBindings.report(track, binding, new ExpressionBindings.Status(null, -1, result, false, lets, own));
                wrote = true;
            } catch (ExprError error) {
                report(track, binding, error, lets);
            } catch (StackOverflowError error) {
                report(track, binding, new ExprError("too deeply nested"), lets);
            }
        }
        return wrote ? after : null;
    }

    private static void report(KeyframeTrack track, ExpressionBinding binding, ExprError error, Map<String, Value> lets) {
        ExpressionBindings.report(track, binding,
                new ExpressionBindings.Status(error.getMessage(), error.position(), null, false, lets, null));
    }

    /** The compiled program, cached; throws its parse error. */
    static Program program(String source, boolean template) {
        Compiled compiled = compile(source, template);
        if (compiled.error() != null) throw compiled.error();
        return compiled.program();
    }

    private static Compiled compile(String source, boolean template) {
        String key = (template ? "t" : "e") + source;
        Compiled compiled = PROGRAMS.get(key);
        if (compiled != null) return compiled;
        try {
            compiled = new Compiled(template ? Program.template(source) : Program.expression(source), null);
        } catch (ExprError error) {
            compiled = new Compiled(null, error);
        }
        PROGRAMS.put(key, compiled);
        return compiled;
    }

    static @Nullable Value own(@Nullable Object snapshot, int component) {
        return switch (snapshot) {
            case float[] floats when component >= 0 -> component < floats.length ? Value.of(floats[component]) : null;
            case float[] floats -> {
                double[] values = new double[floats.length];
                for (int i = 0; i < floats.length; i++) values[i] = floats[i];
                yield floats.length == 1 ? Value.of(values[0]) : new Value.Vec(values);
            }
            case int[] ints when component >= 0 -> component < ints.length ? Value.of(ints[component]) : null;
            case int[] ints -> {
                double[] values = new double[ints.length];
                for (int i = 0; i < ints.length; i++) values[i] = ints[i];
                yield ints.length == 1 ? Value.of(values[0]) : new Value.Vec(values);
            }
            case Number number -> Value.of(number.doubleValue());
            case Boolean bool -> Value.of(bool);
            case String text -> Value.text(text);
            case null, default -> null;
        };
    }

    private static Object write(Object target, int component, Value result) {
        switch (target) {
            case float[] floats -> {
                double[] values = components(result, component, floats.length);
                for (int i = 0; i < floats.length; i++) if (component < 0 || i == component) floats[i] = (float) values[i];
                return floats;
            }
            case int[] ints -> {
                double[] values = components(result, component, ints.length);
                for (int i = 0; i < ints.length; i++) if (component < 0 || i == component) ints[i] = (int) Math.round(values[i]);
                return ints;
            }
            case Integer ignored -> {
                return (int) Math.round(finite(result.number()));
            }
            case Float ignored -> {
                return (float) finite(result.number());
            }
            case Double ignored -> {
                return finite(result.number());
            }
            case Boolean ignored -> {
                return result.truthy();
            }
            case String ignored -> {
                return result.text();
            }
            default -> throw new ExprError("this parameter can't be driven by an expression");
        }
    }

    private static double[] components(Value result, int component, int size) {
        double[] values = new double[size];
        if (component >= 0) {
            if (component < size) values[component] = finite(result.number());
            return values;
        }
        double[] given = result.components(size);
        for (int i = 0; i < size; i++) values[i] = finite(given[i]);
        return values;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new ExprError("the result is " + Value.format(value));
        return value;
    }

    private record TrackScope(float at, @Nullable Value own, References.@Nullable SelfRef driven,
            Map<String, Value> lets) implements Scope {
        @Override
        public Value global(String name) {
            return Globals.get(name, at);
        }

        @Override
        public List<String> globalNames() {
            return Globals.names();
        }

        @Override
        public void let(String name, Value value) {
            lets.put(name, value);
        }

        @Override
        public double tick() {
            return at;
        }

        @Override
        public Value value() {
            if (own == null) throw new ExprError("value is not available here");
            return own;
        }

        @Override
        public Value track(String name) {
            return References.track(name, at);
        }

        @Override
        public Value entity(String id) {
            return References.entity(id);
        }

        @Override
        public Value camera() {
            return References.camera();
        }

        @Override
        public Value self() {
            if (driven == null) throw new ExprError("self is not available here");
            return driven;
        }
    }
}
