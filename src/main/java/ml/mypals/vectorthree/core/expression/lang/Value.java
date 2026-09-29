package ml.mypals.vectorthree.core.expression.lang;

import java.util.List;
import java.util.Locale;

/** What an expression evaluates to: a number, text, a vector, or a handle with members (a track, an entity...). */
public sealed interface Value permits Value.Num, Value.Str, Value.Vec, Value.Obj {
    Num ZERO = new Num(0), ONE = new Num(1);

    record Num(double value) implements Value {}

    record Str(String value) implements Value {}

    record Vec(double[] values) implements Value {}

    non-sealed interface Obj extends Value {
        Value member(String name);

        default Value index(int index) {
            throw new ExprError("cannot index " + describe());
        }

        default Value call(String name, Value[] args) {
            throw new ExprError(describe() + " has no " + name + "()");
        }

        /** Member names, for completion; methods end with "()". */
        default List<String> members() {
            return List.of();
        }

        String describe();
    }

    static Num of(double value) {
        return new Num(value);
    }

    static Num of(boolean value) {
        return value ? ONE : ZERO;
    }

    static Vec vec(double... values) {
        return new Vec(values);
    }

    static Str text(String value) {
        return new Str(value);
    }

    /** A short type name for editors: number, text, vec3, or what an object is. */
    default String type() {
        return switch (this) {
            case Num ignored -> "number";
            case Str ignored -> "text";
            case Vec vec -> "vec" + vec.values.length;
            case Obj obj -> obj.describe();
        };
    }

    default double number() {
        return switch (this) {
            case Num num -> num.value;
            case Str str -> {
                try {
                    yield Double.parseDouble(str.value.trim());
                } catch (NumberFormatException e) {
                    throw new ExprError("expected a number, got text \"" + str.value + "\"");
                }
            }
            case Vec vec when vec.values.length == 1 -> vec.values[0];
            case Vec vec -> throw new ExprError("expected a number, got a vector of " + vec.values.length);
            case Obj obj -> throw new ExprError("expected a number, got " + obj.describe());
        };
    }

    default String text() {
        return switch (this) {
            case Num num -> format(num.value);
            case Str str -> str.value;
            case Vec vec -> {
                StringBuilder builder = new StringBuilder("(");
                for (int i = 0; i < vec.values.length; i++) {
                    if (i > 0) builder.append(", ");
                    builder.append(format(vec.values[i]));
                }
                yield builder.append(')').toString();
            }
            case Obj obj -> obj.describe();
        };
    }

    default boolean truthy() {
        return switch (this) {
            case Num num -> num.value != 0 && !Double.isNaN(num.value);
            case Str str -> !str.value.isEmpty();
            case Vec vec -> vec.values.length > 0;
            case Obj obj -> true;
        };
    }

    /** The value as {@code size} components; a number fills them all. */
    default double[] components(int size) {
        if (this instanceof Vec vec) {
            double[] result = new double[size];
            for (int i = 0; i < size; i++) result[i] = i < vec.values.length ? vec.values[i] : 0;
            return result;
        }
        double[] result = new double[size];
        java.util.Arrays.fill(result, number());
        return result;
    }

    static String format(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (Double.isInfinite(value)) return value > 0 ? "Infinity" : "-Infinity";
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return Long.toString((long) value);
        String text = String.format(Locale.ROOT, "%.6f", value);
        int end = text.length();
        while (text.charAt(end - 1) == '0') end--;
        if (text.charAt(end - 1) == '.') end--;
        return text.substring(0, end);
    }
}
