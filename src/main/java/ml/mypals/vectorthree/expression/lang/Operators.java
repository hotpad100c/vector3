package ml.mypals.vectorthree.expression.lang;

import java.util.Arrays;
import java.util.function.DoubleBinaryOperator;

final class Operators {
    private Operators() {}

    static Value binary(char op, Value a, Value b) {
        if (op == '+' && (a instanceof Value.Str || b instanceof Value.Str)) return Value.text(a.text() + b.text());
        DoubleBinaryOperator math = switch (op) {
            case '+' -> Double::sum;
            case '-' -> (x, y) -> x - y;
            case '*' -> (x, y) -> x * y;
            case '/' -> (x, y) -> x / y;
            case '%' -> Operators::mod;
            default -> Math::pow;
        };
        return zip(a, b, math);
    }

    static double mod(double x, double y) {
        double r = x % y;
        return r != 0 && (r < 0) != (y < 0) ? r + y : r;
    }

    /** Element-wise over vectors, a number broadcasting to every component. */
    static Value zip(Value a, Value b, DoubleBinaryOperator math) {
        if (a instanceof Value.Vec x && b instanceof Value.Vec y) {
            double[] result = new double[Math.max(x.values().length, y.values().length)];
            for (int i = 0; i < result.length; i++) {
                result[i] = math.applyAsDouble(i < x.values().length ? x.values()[i] : 0, i < y.values().length ? y.values()[i] : 0);
            }
            return new Value.Vec(result);
        }
        if (a instanceof Value.Vec x) {
            double y = b.number();
            double[] result = new double[x.values().length];
            for (int i = 0; i < result.length; i++) result[i] = math.applyAsDouble(x.values()[i], y);
            return new Value.Vec(result);
        }
        if (b instanceof Value.Vec y) {
            double x = a.number();
            double[] result = new double[y.values().length];
            for (int i = 0; i < result.length; i++) result[i] = math.applyAsDouble(x, y.values()[i]);
            return new Value.Vec(result);
        }
        return Value.of(math.applyAsDouble(a.number(), b.number()));
    }

    static Value negate(Value value) {
        if (value instanceof Value.Vec vec) {
            double[] result = vec.values().clone();
            for (int i = 0; i < result.length; i++) result[i] = -result[i];
            return new Value.Vec(result);
        }
        return Value.of(-value.number());
    }

    static boolean equal(Value a, Value b) {
        if (a instanceof Value.Str || b instanceof Value.Str) return a.text().equals(b.text());
        if (a instanceof Value.Vec x && b instanceof Value.Vec y) return Arrays.equals(x.values(), y.values());
        if (a instanceof Value.Obj || b instanceof Value.Obj) return a == b;
        return a.number() == b.number();
    }

    static Value member(Value target, String name) {
        return switch (target) {
            case Value.Obj obj -> obj.member(name);
            case Value.Vec vec -> {
                int component = component(name);
                if (component < 0) throw new ExprError("a vector has no ." + name);
                if (component >= vec.values().length) {
                    throw new ExprError("." + name + " is past the end of a vector of " + vec.values().length);
                }
                yield Value.of(vec.values()[component]);
            }
            default -> throw new ExprError(target.text() + " has no ." + name);
        };
    }

    static int component(String name) {
        return switch (name) {
            case "x", "r" -> 0;
            case "y", "g" -> 1;
            case "z", "b" -> 2;
            case "w", "a" -> 3;
            default -> -1;
        };
    }

    static Value index(Value target, Value at) {
        if (target instanceof Value.Obj obj && at instanceof Value.Str name) return obj.member(name.value());
        int i = (int) Math.floor(at.number());
        return switch (target) {
            case Value.Obj obj -> obj.index(i);
            case Value.Vec vec -> {
                if (i < 0 || i >= vec.values().length) throw new ExprError("index " + i + " is outside 0.." + (vec.values().length - 1));
                yield Value.of(vec.values()[i]);
            }
            case Value.Str str -> {
                if (i < 0 || i >= str.value().length()) throw new ExprError("index " + i + " is outside the text");
                yield Value.text(String.valueOf(str.value().charAt(i)));
            }
            default -> throw new ExprError("cannot index a number");
        };
    }
}
