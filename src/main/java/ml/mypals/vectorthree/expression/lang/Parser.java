package ml.mypals.vectorthree.expression.lang;

import ml.mypals.vectorthree.expression.lang.Lexer.Kind;
import ml.mypals.vectorthree.expression.lang.Lexer.Token;

import java.util.ArrayList;
import java.util.List;

/** Recursive descent straight into closures. Names are resolved here, so unknown functions fail at parse time. */
final class Parser {
    interface Expr {
        Value eval(Scope scope);
    }

    private final List<Token> tokens;
    private int index;
    boolean usesValue;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    static Parser of(String source) {
        return new Parser(Lexer.tokenize(source));
    }

    Expr parseAll() {
        if (peek().kind() == Kind.END) throw new ExprError("empty expression", 0);
        Expr expr = ternary();
        Token rest = peek();
        if (rest.kind() != Kind.END) throw new ExprError("unexpected '" + rest.text() + "'", rest.position());
        return expr;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token next() {
        return tokens.get(index++);
    }

    private boolean accept(String op) {
        if (!peek().is(op)) return false;
        index++;
        return true;
    }

    private Token expect(String op) {
        Token token = next();
        if (!token.is(op)) {
            throw new ExprError("expected '" + op + "'" + (token.kind() == Kind.END ? " at the end" : ", found '" + token.text() + "'"),
                    token.position());
        }
        return token;
    }

    private Expr ternary() {
        Expr condition = or();
        if (!peek().is("?")) return condition;
        next();
        Expr then = ternary();
        expect(":");
        Expr otherwise = ternary();
        return scope -> condition.eval(scope).truthy() ? then.eval(scope) : otherwise.eval(scope);
    }

    private Expr or() {
        Expr left = and();
        while (accept("||")) {
            Expr a = left, b = and();
            left = scope -> Value.of(a.eval(scope).truthy() || b.eval(scope).truthy());
        }
        return left;
    }

    private Expr and() {
        Expr left = equality();
        while (accept("&&")) {
            Expr a = left, b = equality();
            left = scope -> Value.of(a.eval(scope).truthy() && b.eval(scope).truthy());
        }
        return left;
    }

    private Expr equality() {
        Expr left = comparison();
        while (peek().is("==") || peek().is("!=")) {
            boolean equal = next().is("==");
            Expr a = left, b = comparison();
            left = scope -> Value.of(Operators.equal(a.eval(scope), b.eval(scope)) == equal);
        }
        return left;
    }

    private Expr comparison() {
        Expr left = additive();
        while (peek().is("<") || peek().is("<=") || peek().is(">") || peek().is(">=")) {
            Token op = next();
            Expr a = left, b = additive();
            left = located(op.position(), scope -> {
                double x = a.eval(scope).number(), y = b.eval(scope).number();
                return Value.of(switch (op.text()) {
                    case "<" -> x < y;
                    case "<=" -> x <= y;
                    case ">" -> x > y;
                    default -> x >= y;
                });
            });
        }
        return left;
    }

    private Expr additive() {
        Expr left = multiplicative();
        while (peek().is("+") || peek().is("-")) {
            Token op = next();
            left = binary(op, left, multiplicative());
        }
        return left;
    }

    private Expr multiplicative() {
        Expr left = unary();
        while (peek().is("*") || peek().is("/") || peek().is("%")) {
            Token op = next();
            left = binary(op, left, unary());
        }
        return left;
    }

    private Expr unary() {
        Token token = peek();
        if (accept("-")) {
            Expr operand = unary();
            return located(token.position(), scope -> Operators.negate(operand.eval(scope)));
        }
        if (accept("+")) return unary();
        if (accept("!")) {
            Expr operand = unary();
            return scope -> Value.of(!operand.eval(scope).truthy());
        }
        return power();
    }

    private Expr power() {
        Expr base = postfix();
        Token op = peek();
        if (!accept("^")) return base;
        return binary(op, base, unary());
    }

    private Expr binary(Token op, Expr a, Expr b) {
        char symbol = op.text().charAt(0);
        return located(op.position(), scope -> Operators.binary(symbol, a.eval(scope), b.eval(scope)));
    }

    private Expr postfix() {
        Expr expr = primary();
        while (true) {
            Token token = peek();
            if (accept(".")) {
                Token name = next();
                if (name.kind() != Kind.IDENT) throw new ExprError("expected a name after '.'", name.position());
                Expr target = expr;
                expr = located(name.position(), scope -> Operators.member(target.eval(scope), name.text()));
            } else if (accept("[")) {
                Expr target = expr, at = ternary();
                expect("]");
                expr = located(token.position(), scope -> Operators.index(target.eval(scope), at.eval(scope)));
            } else {
                return expr;
            }
        }
    }

    private Expr primary() {
        Token token = next();
        switch (token.kind()) {
            case NUMBER -> {
                Value value = Value.of(token.number());
                return scope -> value;
            }
            case STRING -> {
                Value value = Value.text(token.text());
                return scope -> value;
            }
            case IDENT -> {
                if (peek().is("(")) return call(token);
                return name(token);
            }
            case OP -> {
                if (token.is("(")) {
                    Expr inner = ternary();
                    expect(")");
                    return inner;
                }
            }
            case END -> throw new ExprError("unexpected end", token.position());
        }
        throw new ExprError("unexpected '" + token.text() + "'", token.position());
    }

    private Expr name(Token token) {
        String name = token.text();
        switch (name) {
            case "pi" -> { Value value = Value.of(Math.PI); return scope -> value; }
            case "e" -> { Value value = Value.of(Math.E); return scope -> value; }
            case "true" -> { return scope -> Value.ONE; }
            case "false" -> { return scope -> Value.ZERO; }
            case "time" -> { return scope -> Value.of(scope.tick() / 20.0); }
            case "tick" -> { return scope -> Value.of(scope.tick()); }
            case "value" -> {
                usesValue = true;
                return located(token.position(), Scope::value);
            }
            case "camera" -> { return located(token.position(), Scope::camera); }
            case "self" -> { return located(token.position(), Scope::self); }
            default -> {
                // A bare name is a track: Cube.position.y is track("Cube").position.y.
                return located(token.position(), scope -> scope.track(name));
            }
        }
    }

    private Expr call(Token name) {
        Functions.Fn fn = Functions.get(name.text());
        if (fn == null) throw new ExprError("unknown function " + name.text() + "()", name.position());
        expect("(");
        List<Expr> args = new ArrayList<>();
        if (!peek().is(")")) {
            do args.add(ternary()); while (accept(","));
        }
        expect(")");
        if (args.size() < fn.min() || fn.max() >= 0 && args.size() > fn.max()) {
            String expected = fn.min() == fn.max() ? Integer.toString(fn.min())
                    : fn.max() < 0 ? "at least " + fn.min() : fn.min() + " to " + fn.max();
            throw new ExprError(name.text() + "() takes " + expected + " arguments, got " + args.size(), name.position());
        }
        Expr[] compiled = args.toArray(Expr[]::new);
        return located(name.position(), scope -> {
            Value[] values = new Value[compiled.length];
            for (int i = 0; i < compiled.length; i++) values[i] = compiled[i].eval(scope);
            return fn.body().call(scope, values);
        });
    }

    private static Expr located(int position, Expr expr) {
        return scope -> {
            try {
                return expr.eval(scope);
            } catch (ExprError error) {
                throw error.at(position);
            } catch (ArithmeticException | IndexOutOfBoundsException | IllegalArgumentException error) {
                throw new ExprError(String.valueOf(error.getMessage()), position);
            }
        };
    }
}
