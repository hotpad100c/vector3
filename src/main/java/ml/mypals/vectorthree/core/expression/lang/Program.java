package ml.mypals.vectorthree.core.expression.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A compiled expression: {@code let name = ...;} statements, then the result. Text parameters use templates instead:
 * literal text with {expressions} in braces ("Score: {round(time * 10)}"), where {{ and }} are literal braces; a let
 * in one {...} can be used in the later ones, and a {...} of only lets shows nothing.
 */
public final class Program {
    private final Parser.Expr expr;
    private final boolean usesValue;
    private final Map<String, Integer> locals;

    private Program(Parser.Expr expr, boolean usesValue, Map<String, Integer> locals) {
        this.expr = expr;
        this.usesValue = usesValue;
        this.locals = locals;
    }

    public static Program expression(String source) {
        Map<String, Integer> locals = new LinkedHashMap<>();
        Parser parser = Parser.of(source, locals);
        Parser.Expr expr = parser.parseProgram(false);
        return new Program(expr, parser.usesValue, locals);
    }

    public static Program template(String source) {
        Map<String, Integer> locals = new LinkedHashMap<>();
        List<Parser.Expr> parts = new ArrayList<>();
        boolean usesValue = false;
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '}') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '}') i++;
                literal.append('}');
                i++;
                continue;
            }
            if (c != '{') {
                literal.append(c);
                i++;
                continue;
            }
            if (i + 1 < source.length() && source.charAt(i + 1) == '{') {
                literal.append('{');
                i += 2;
                continue;
            }
            int end = closing(source, i + 1);
            if (end < 0) throw new ExprError("unclosed '{'", i);
            if (!literal.isEmpty()) {
                Value text = Value.text(literal.toString());
                parts.add(scope -> text);
                literal.setLength(0);
            }
            int offset = i + 1;
            try {
                Parser parser = Parser.of(source.substring(offset, end), locals);
                Parser.Expr inner = parser.parseProgram(true);
                usesValue |= parser.usesValue;
                parts.add(scope -> {
                    try {
                        return inner.eval(scope);
                    } catch (ExprError error) {
                        throw error.shifted(offset);
                    }
                });
            } catch (ExprError error) {
                throw error.shifted(offset);
            }
            i = end + 1;
        }
        if (!literal.isEmpty() || parts.isEmpty()) {
            Value text = Value.text(literal.toString());
            parts.add(scope -> text);
        }
        Parser.Expr[] all = parts.toArray(Parser.Expr[]::new);
        if (all.length == 1) {
            Parser.Expr only = all[0];
            return new Program(scope -> Value.text(only.eval(scope).text()), usesValue, locals);
        }
        return new Program(scope -> {
            StringBuilder builder = new StringBuilder();
            for (Parser.Expr part : all) builder.append(part.eval(scope).text());
            return Value.text(builder.toString());
        }, usesValue, locals);
    }

    private static int closing(String source, int from) {
        char quote = 0;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i + 1 < source.length() && source.charAt(i + 1) != '\n') i++;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '}') {
                return i;
            }
        }
        return -1;
    }

    public Value eval(Scope scope) {
        return expr.eval(new Frame(scope, locals.size()));
    }

    /** The names its lets define, in order. */
    public Set<String> locals() {
        return locals.keySet();
    }

    public boolean usesValue() {
        return usesValue;
    }
}
