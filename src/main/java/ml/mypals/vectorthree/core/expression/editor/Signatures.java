package ml.mypals.vectorthree.core.expression.editor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parameter lists of the built-in functions, for completion and the reference tab. Descriptions are lang keys. */
public final class Signatures {
    public static final Map<String, String> FUNCTIONS = new LinkedHashMap<>();
    public static final List<String> BUILTINS = List.of("time", "tick", "value", "self", "camera", "global", "pi", "e",
            "true", "false", "let");

    static {
        for (String name : List.of("sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "exp", "log", "abs", "sign",
                "floor", "ceil", "fract", "rad", "deg")) {
            FUNCTIONS.put(name, "(x)");
        }
        FUNCTIONS.put("atan2", "(y, x)");
        FUNCTIONS.put("round", "(x[, digits])");
        FUNCTIONS.put("pow", "(a, b)");
        FUNCTIONS.put("mod", "(a, b)");
        FUNCTIONS.put("min", "(a, b, ...)");
        FUNCTIONS.put("max", "(a, b, ...)");
        FUNCTIONS.put("clamp", "(x, lo, hi)");
        FUNCTIONS.put("lerp", "(a, b, t)");
        FUNCTIONS.put("step", "(edge, x)");
        FUNCTIONS.put("smoothstep", "(e0, e1, x)");
        FUNCTIONS.put("remap", "(x, inMin, inMax, outMin, outMax)");
        FUNCTIONS.put("rand", "(seed[, min, max])");
        FUNCTIONS.put("noise", "(x[, seed])");
        FUNCTIONS.put("wiggle", "(freq, amount[, seed])");
        FUNCTIONS.put("vec", "(x, y[, z, w])");
        FUNCTIONS.put("dot", "(a, b)");
        FUNCTIONS.put("cross", "(a, b)");
        FUNCTIONS.put("length", "(v)");
        FUNCTIONS.put("len", "(v)");
        FUNCTIONS.put("distance", "(a, b)");
        FUNCTIONS.put("normalize", "(v)");
        FUNCTIONS.put("str", "(x[, digits])");
        FUNCTIONS.put("format", "(pattern, ...)");
        FUNCTIONS.put("pad", "(x, width[, fill])");
        FUNCTIONS.put("upper", "(s)");
        FUNCTIONS.put("lower", "(s)");
        FUNCTIONS.put("substr", "(s, start[, length])");
        FUNCTIONS.put("num", "(s)");
        FUNCTIONS.put("track", "(\"name\")");
        FUNCTIONS.put("entity", "(\"name or UUID\")");
        FUNCTIONS.put("uuid", "(value)");
    }

    private Signatures() {}

    public static String of(String function) {
        return FUNCTIONS.getOrDefault(function, "(...)");
    }

    public static String descriptionKey(String function) {
        return "vector3.expression.fn." + function;
    }
}
