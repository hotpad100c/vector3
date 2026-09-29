package ml.mypals.vectorthree.expression.lang;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;

/** The built-in functions. Random and noise are seeded and depend only on their arguments, so exports repeat. */
public final class Functions {
    public interface Body {
        Value call(Scope scope, Value[] args);
    }

    public record Fn(int min, int max, Body body) {}

    private static final Map<String, Fn> FUNCTIONS = new TreeMap<>();

    static {
        math("sin", Math::sin);
        math("cos", Math::cos);
        math("tan", Math::tan);
        math("asin", Math::asin);
        math("acos", Math::acos);
        math("atan", Math::atan);
        math("sqrt", Math::sqrt);
        math("abs", Math::abs);
        math("floor", Math::floor);
        math("ceil", Math::ceil);
        math("sign", Math::signum);
        math("fract", x -> x - Math.floor(x));
        math("rad", Math::toRadians);
        math("deg", Math::toDegrees);
        math("exp", Math::exp);
        math("log", Math::log);
        add("atan2", 2, 2, (scope, a) -> Operators.zip(a[0], a[1], Math::atan2));
        add("pow", 2, 2, (scope, a) -> Operators.zip(a[0], a[1], Math::pow));
        add("mod", 2, 2, (scope, a) -> Operators.zip(a[0], a[1], Operators::mod));
        add("round", 1, 2, (scope, a) -> {
            double scale = a.length > 1 ? Math.pow(10, a[1].number()) : 1;
            return map(a[0], x -> Math.round(x * scale) / scale);
        });
        add("min", 1, -1, (scope, a) -> fold(a, Math::min));
        add("max", 1, -1, (scope, a) -> fold(a, Math::max));
        add("clamp", 3, 3, (scope, a) -> Operators.zip(Operators.zip(a[0], a[1], Math::max), a[2], Math::min));
        add("lerp", 3, 3, (scope, a) -> {
            Value t = a[2];
            return Operators.binary('+', a[0], Operators.binary('*', Operators.binary('-', a[1], a[0]), t));
        });
        add("step", 2, 2, (scope, a) -> Operators.zip(a[0], a[1], (edge, x) -> x < edge ? 0 : 1));
        add("smoothstep", 3, 3, (scope, a) -> {
            double e0 = a[0].number(), e1 = a[1].number();
            return map(a[2], x -> {
                double t = Math.clamp((x - e0) / (e1 - e0), 0, 1);
                return t * t * (3 - 2 * t);
            });
        });
        add("remap", 5, 5, (scope, a) -> {
            double inMin = a[1].number(), inMax = a[2].number();
            double t = Math.clamp((a[0].number() - inMin) / (inMax - inMin), 0, 1);
            return Operators.binary('+', a[3], Operators.binary('*', Operators.binary('-', a[4], a[3]), Value.of(t)));
        });

        add("rand", 1, 3, (scope, a) -> {
            double r = random(Double.doubleToLongBits(a[0].number()));
            if (a.length == 1) return Value.of(r);
            double lo = a.length == 3 ? a[1].number() : 0, hi = a.length == 3 ? a[2].number() : a[1].number();
            return Value.of(lo + r * (hi - lo));
        });
        add("noise", 1, 2, (scope, a) -> Value.of(noise(a[0].number(), a.length > 1 ? (long) a[1].number() : 0)));
        add("wiggle", 2, 3, (scope, a) -> {
            double t = scope.tick() / 20.0 * a[0].number();
            long seed = a.length > 2 ? (long) a[2].number() : 0;
            return Value.of(a[1].number() * (noise(t, seed) + 0.5 * noise(t * 2, seed + 1)) / 1.5);
        });

        add("vec", 1, 4, (scope, a) -> {
            double[] values = new double[a.length];
            for (int i = 0; i < a.length; i++) values[i] = a[i].number();
            return new Value.Vec(values);
        });
        add("length", 1, 1, (scope, a) -> a[0] instanceof Value.Str s ? Value.of(s.value().length()) : Value.of(norm(vector(a[0]))));
        add("len", 1, 1, (scope, a) -> a[0] instanceof Value.Str s ? Value.of(s.value().length())
                : a[0] instanceof Value.Vec v ? Value.of(v.values().length) : Value.ONE);
        add("distance", 2, 2, (scope, a) -> Value.of(norm(vector(Operators.binary('-', a[0], a[1])))));
        add("normalize", 1, 1, (scope, a) -> {
            double[] v = vector(a[0]);
            double n = norm(v);
            return n == 0 ? new Value.Vec(v) : Operators.binary('/', new Value.Vec(v), Value.of(n));
        });
        add("dot", 2, 2, (scope, a) -> {
            double[] x = vector(a[0]), y = vector(a[1]);
            double sum = 0;
            for (int i = 0; i < Math.min(x.length, y.length); i++) sum += x[i] * y[i];
            return Value.of(sum);
        });
        add("cross", 2, 2, (scope, a) -> {
            double[] x = a[0].components(3), y = a[1].components(3);
            return Value.vec(x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]);
        });

        add("str", 1, 2, (scope, a) -> a.length == 1 ? Value.text(a[0].text())
                : Value.text(String.format(Locale.ROOT, "%." + Math.max(0, (int) a[1].number()) + "f", a[0].number())));
        add("format", 1, -1, (scope, a) -> Value.text(format(a)));
        add("upper", 1, 1, (scope, a) -> Value.text(a[0].text().toUpperCase(Locale.ROOT)));
        add("lower", 1, 1, (scope, a) -> Value.text(a[0].text().toLowerCase(Locale.ROOT)));
        add("substr", 2, 3, (scope, a) -> {
            String text = a[0].text();
            int start = Math.clamp((long) a[1].number(), 0, text.length());
            int end = a.length > 2 ? Math.clamp(start + (long) a[2].number(), start, text.length()) : text.length();
            return Value.text(text.substring(start, end));
        });
        add("pad", 2, 3, (scope, a) -> {
            String text = a[0].text(), fill = a.length > 2 ? a[2].text() : " ";
            int width = (int) a[1].number();
            if (fill.isEmpty() || text.length() >= width) return Value.text(text);
            StringBuilder builder = new StringBuilder();
            while (builder.length() + text.length() < width) builder.append(fill);
            return Value.text(builder.substring(0, width - text.length()) + text);
        });
        add("num", 1, 1, (scope, a) -> Value.of(a[0].number()));

        add("track", 1, 1, (scope, a) -> scope.track(a[0].text()));
        add("entity", 1, 1, (scope, a) -> scope.entity(a[0].text()));
    }

    private Functions() {}

    static @Nullable Fn get(String name) {
        return FUNCTIONS.get(name);
    }

    public static List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(FUNCTIONS.keySet()));
    }

    private static void add(String name, int min, int max, Body body) {
        FUNCTIONS.put(name, new Fn(min, max, body));
    }

    private static void math(String name, DoubleUnaryOperator op) {
        add(name, 1, 1, (scope, a) -> map(a[0], op));
    }

    private static Value map(Value value, DoubleUnaryOperator op) {
        if (value instanceof Value.Vec vec) {
            double[] result = new double[vec.values().length];
            for (int i = 0; i < result.length; i++) result[i] = op.applyAsDouble(vec.values()[i]);
            return new Value.Vec(result);
        }
        return Value.of(op.applyAsDouble(value.number()));
    }

    private static Value fold(Value[] args, java.util.function.DoubleBinaryOperator op) {
        Value result = args[0];
        for (int i = 1; i < args.length; i++) result = Operators.zip(result, args[i], op);
        return result;
    }

    private static double[] vector(Value value) {
        return value instanceof Value.Vec vec ? vec.values() : new double[]{value.number()};
    }

    private static double norm(double[] v) {
        double sum = 0;
        for (double x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    private static String format(Value[] a) {
        Object[] args = new Object[a.length - 1];
        for (int i = 1; i < a.length; i++) args[i - 1] = a[i] instanceof Value.Str s ? s.value() : a[i].number();
        try {
            return String.format(Locale.ROOT, a[0].text(), args);
        } catch (IllegalFormatException wrongType) {
            // %d wants a whole number; try again with the whole numbers as longs.
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Double d && d == Math.rint(d)) args[i] = d.longValue();
            }
            try {
                return String.format(Locale.ROOT, a[0].text(), args);
            } catch (IllegalFormatException e) {
                throw new ExprError("format: " + e.getMessage());
            }
        }
    }

    static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    static double random(long seed) {
        return (mix(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }

    /** 1D gradient noise in about [-1, 1]. */
    static double noise(double x, long seed) {
        long cell = (long) Math.floor(x);
        double f = x - cell;
        double g0 = random(cell * 31 + seed * 0x51ED27L) * 2 - 1;
        double g1 = random((cell + 1) * 31 + seed * 0x51ED27L) * 2 - 1;
        double fade = f * f * f * (f * (f * 6 - 15) + 10);
        return 2 * (g0 * f + fade * (g1 * (f - 1) - g0 * f));
    }
}
