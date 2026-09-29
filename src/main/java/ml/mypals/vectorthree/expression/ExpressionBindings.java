package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.expression.lang.Value;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** The expressions on a track, and what each did the last time it ran. */
public final class ExpressionBindings {
    /** Implemented on KeyframeTrack; saved with the track by Flashback's reflective Gson. */
    public interface Holder {
        ExpressionBinding @Nullable [] vector3$expressions();

        void vector3$setExpressions(ExpressionBinding @Nullable [] expressions);
    }

    /** {@code error} is null when it ran; {@code missing} when its widget wasn't in the editor this time. */
    public record Status(@Nullable String error, int position, @Nullable Value result, boolean missing,
            Map<String, Value> lets, @Nullable Value own) {
        static final Status MISSING = new Status(null, -1, null, true, Map.of(), null);
    }

    private static final Map<KeyframeTrack, Map<ExpressionBinding, Status>> STATUS = new WeakHashMap<>();

    private ExpressionBindings() {}

    public static List<ExpressionBinding> of(KeyframeTrack track) {
        ExpressionBinding[] bindings = ((Holder) track).vector3$expressions();
        return bindings == null ? List.of() : List.of(bindings);
    }

    public static boolean any(KeyframeTrack track) {
        ExpressionBinding[] bindings = ((Holder) track).vector3$expressions();
        return bindings != null && bindings.length > 0;
    }

    public static void set(KeyframeTrack track, List<ExpressionBinding> bindings) {
        ((Holder) track).vector3$setExpressions(bindings.isEmpty() ? null : bindings.toArray(ExpressionBinding[]::new));
    }

    public static @Nullable ExpressionBinding find(KeyframeTrack track, String widget, int component) {
        for (ExpressionBinding binding : of(track)) if (binding.targets(widget, component)) return binding;
        return null;
    }

    /** Adds, replaces (same widget and component) or, with a null {@code binding}, removes. */
    public static void put(KeyframeTrack track, String widget, int component, @Nullable ExpressionBinding binding) {
        List<ExpressionBinding> next = new ArrayList<>();
        boolean placed = false;
        for (ExpressionBinding existing : of(track)) {
            if (!existing.targets(widget, component)) {
                next.add(existing);
            } else if (binding != null && !placed) {
                next.add(binding);
                placed = true;
            }
        }
        if (binding != null && !placed) next.add(binding);
        set(track, next);
    }

    public static @Nullable Status status(KeyframeTrack track, ExpressionBinding binding) {
        Map<ExpressionBinding, Status> byBinding = STATUS.get(track);
        return byBinding == null ? null : byBinding.get(binding);
    }

    static void report(KeyframeTrack track, ExpressionBinding binding, Status status) {
        STATUS.computeIfAbsent(track, key -> new HashMap<>()).put(binding, status);
    }
}
