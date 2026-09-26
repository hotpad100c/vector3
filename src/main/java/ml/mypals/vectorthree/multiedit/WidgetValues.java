package ml.mypals.vectorthree.multiedit;

import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImDouble;
import imgui.moulberry90.type.ImFloat;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Snapshots, comparisons and injection for the value holders ImGui widgets edit in place. */
final class WidgetValues {
    private static final float EPSILON = 1.0e-4f;

    private WidgetValues() {}

    static Object snapshot(@Nullable Object container) {
        return switch (container) {
            case null -> "";
            case float[] floats -> floats.clone();
            case int[] ints -> ints.clone();
            case ImBoolean value -> value.get();
            case ImInt value -> value.get();
            case ImFloat value -> value.get();
            case ImDouble value -> value.get();
            case ImString value -> value.get();
            default -> container;
        };
    }

    static int size(Object value) {
        if (value instanceof float[] floats) return floats.length;
        if (value instanceof int[] ints) return ints.length;
        return 1;
    }

    static boolean any(boolean[] flags) {
        for (boolean flag : flags) if (flag) return true;
        return false;
    }

    /** Per component, whether the targets that have this widget disagree. */
    static boolean[] mixed(String key, Object primary, @Nullable List<Map<String, Object>> captures) {
        boolean[] mixed = new boolean[size(primary)];
        if (captures == null) return mixed;
        Object first = null;
        for (Map<String, Object> capture : captures) {
            Object value = capture.get(key);
            if (value == null) continue;
            if (first == null) {
                first = value;
                continue;
            }
            for (int i = 0; i < mixed.length; i++) mixed[i] |= !equal(first, value, i);
        }
        return mixed;
    }

    static boolean[] changed(Object before, Object after) {
        boolean[] changed = new boolean[size(after)];
        for (int i = 0; i < changed.length; i++) changed[i] = !equal(before, after, i);
        if (!any(changed)) Arrays.fill(changed, true);
        return changed;
    }

    private static boolean equal(Object a, Object b, int index) {
        if (a instanceof float[] x && b instanceof float[] y) {
            return index >= x.length || index >= y.length || Math.abs(x[index] - y[index]) < EPSILON;
        }
        if (a instanceof int[] x && b instanceof int[] y) {
            return index >= x.length || index >= y.length || x[index] == y[index];
        }
        if (a instanceof Float x && b instanceof Float y) return Math.abs(x - y) < EPSILON;
        if (a instanceof Double x && b instanceof Double y) return Math.abs(x - y) < EPSILON;
        return Objects.equals(a, b);
    }

    /** Writes the replayed edit into this target's holder and returns what the widget reports. */
    static boolean inject(MultiEditSession.Change edit, @Nullable Object container, MultiEditSession.Kind kind) {
        return switch (kind) {
            case BUTTON, COMBO -> true;
            case CHECK, RADIO -> container instanceof Boolean current && !current.equals(edit.value());
            case VALUE -> {
                write(edit, container);
                yield true;
            }
        };
    }

    private static void write(MultiEditSession.Change edit, @Nullable Object container) {
        Object value = edit.value();
        boolean[] components = edit.components();
        switch (container) {
            case float[] floats when value instanceof float[] source -> {
                for (int i = 0; i < Math.min(floats.length, source.length); i++) {
                    if (components == null || components.length <= i || components[i]) floats[i] = source[i];
                }
            }
            case int[] ints when value instanceof int[] source -> {
                for (int i = 0; i < Math.min(ints.length, source.length); i++) {
                    if (components == null || components.length <= i || components[i]) ints[i] = source[i];
                }
            }
            case ImBoolean holder when value instanceof Boolean v -> holder.set(v);
            case ImInt holder when value instanceof Integer v -> holder.set(v);
            case ImFloat holder when value instanceof Float v -> holder.set(v);
            case ImDouble holder when value instanceof Double v -> holder.set(v);
            case ImString holder when value instanceof String v -> holder.set(v);
            case null, default -> {}
        }
    }
}
