package ml.mypals.vectorthree.core.expression.lang;

import java.util.ArrayList;
import java.util.List;

final class Lexer {
    enum Kind { NUMBER, STRING, IDENT, OP, END }

    record Token(Kind kind, String text, double number, int position) {
        boolean is(String op) {
            return kind == Kind.OP && text.equals(op);
        }
    }

    private static final String[] OPERATORS = {"==", "!=", "<=", ">=", "&&", "||",
            "+", "-", "*", "/", "%", "^", "(", ")", "[", "]", ",", ".", "?", ":", "<", ">", "!", "=", ";"};

    private Lexer() {}

    static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0, length = source.length();
        while (i < length) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
                while (i < length && source.charAt(i) != '\n') i++;
            } else if (Character.isDigit(c) || c == '.' && i + 1 < length && Character.isDigit(source.charAt(i + 1))) {
                int start = i;
                while (i < length && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
                if (i < length && (source.charAt(i) == 'e' || source.charAt(i) == 'E')) {
                    int exponent = i + 1;
                    if (exponent < length && (source.charAt(exponent) == '+' || source.charAt(exponent) == '-')) exponent++;
                    if (exponent < length && Character.isDigit(source.charAt(exponent))) {
                        i = exponent;
                        while (i < length && Character.isDigit(source.charAt(i))) i++;
                    }
                }
                String text = source.substring(start, i);
                try {
                    tokens.add(new Token(Kind.NUMBER, text, Double.parseDouble(text), start));
                } catch (NumberFormatException e) {
                    throw new ExprError("bad number \"" + text + "\"", start);
                }
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < length && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;
                tokens.add(new Token(Kind.IDENT, source.substring(start, i), 0, start));
            } else if (c == '"' || c == '\'') {
                int start = i++;
                StringBuilder text = new StringBuilder();
                while (true) {
                    if (i >= length) throw new ExprError("unclosed text", start);
                    char next = source.charAt(i++);
                    if (next == c) break;
                    if (next == '\\' && i < length) {
                        char escaped = source.charAt(i++);
                        text.append(switch (escaped) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            default -> escaped;
                        });
                    } else {
                        text.append(next);
                    }
                }
                tokens.add(new Token(Kind.STRING, text.toString(), 0, start));
            } else {
                String op = null;
                for (String candidate : OPERATORS) {
                    if (source.startsWith(candidate, i)) {
                        op = candidate;
                        break;
                    }
                }
                if (op == null) throw new ExprError("unexpected '" + c + "'", i);
                tokens.add(new Token(Kind.OP, op, 0, i));
                i += op.length();
            }
        }
        tokens.add(new Token(Kind.END, "", 0, length));
        return tokens;
    }
}
