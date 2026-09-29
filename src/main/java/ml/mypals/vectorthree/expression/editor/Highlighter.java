package ml.mypals.vectorthree.expression.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Splits an expression (or a template) into styled spans for display. Never fails: bad input is plain text. */
public final class Highlighter {
    public enum Style { PLAIN, LITERAL, COMMENT, STRING, NUMBER, KEYWORD, BUILTIN, FUNCTION, MEMBER, LOCAL, TRACK, OPERATOR, BRACE }

    public record Span(int start, int end, Style style) {}

    private static final Set<String> KEYWORDS = Set.of("let", "true", "false");
    private static final Set<String> BUILTINS = Set.of("time", "tick", "value", "self", "camera", "global", "pi", "e");

    private Highlighter() {}

    public static List<Span> spans(String source, boolean template) {
        List<Span> spans = new ArrayList<>();
        Set<String> locals = locals(source);
        if (!template) {
            expression(source, 0, source.length(), locals, spans);
            return spans;
        }
        int i = 0, literal = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            boolean doubled = i + 1 < source.length() && source.charAt(i + 1) == c;
            if ((c == '{' || c == '}') && doubled) {
                i += 2;
                continue;
            }
            if (c != '{') {
                i++;
                continue;
            }
            if (literal < i) spans.add(new Span(literal, i, Style.LITERAL));
            int end = closing(source, i + 1);
            spans.add(new Span(i, i + 1, Style.BRACE));
            int stop = end < 0 ? source.length() : end;
            expression(source, i + 1, stop, locals, spans);
            if (end >= 0) spans.add(new Span(end, end + 1, Style.BRACE));
            i = stop + 1;
            literal = i;
        }
        if (literal < source.length()) spans.add(new Span(literal, source.length(), Style.LITERAL));
        return spans;
    }

    /** Names defined by let anywhere in the source. */
    public static Set<String> locals(String source) {
        Set<String> names = new HashSet<>();
        int at = 0;
        while ((at = source.indexOf("let", at)) >= 0) {
            boolean start = at == 0 || !identifierPart(source.charAt(at - 1));
            int i = at + 3;
            if (start && i < source.length() && Character.isWhitespace(source.charAt(i))) {
                while (i < source.length() && Character.isWhitespace(source.charAt(i))) i++;
                int name = i;
                while (i < source.length() && identifierPart(source.charAt(i))) i++;
                if (i > name) names.add(source.substring(name, i));
            }
            at += 3;
        }
        return names;
    }

    private static void expression(String source, int from, int to, Set<String> locals, List<Span> spans) {
        int i = from;
        while (i < to) {
            char c = source.charAt(i);
            int start = i;
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < to && source.charAt(i + 1) == '/') {
                while (i < to && source.charAt(i) != '\n') i++;
                spans.add(new Span(start, i, Style.COMMENT));
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < to && source.charAt(i) != c) i += source.charAt(i) == '\\' ? 2 : 1;
                i = Math.min(to, i + 1);
                spans.add(new Span(start, i, Style.STRING));
            } else if (Character.isDigit(c) || c == '.' && i + 1 < to && Character.isDigit(source.charAt(i + 1))
                    && (i == from || !identifierPart(source.charAt(i - 1)))) {
                while (i < to && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
                spans.add(new Span(start, i, Style.NUMBER));
            } else if (Character.isLetter(c) || c == '_') {
                while (i < to && identifierPart(source.charAt(i))) i++;
                String name = source.substring(start, i);
                spans.add(new Span(start, i, style(source, start, i, to, name, locals)));
            } else {
                i++;
                spans.add(new Span(start, i, Style.OPERATOR));
            }
        }
    }

    private static Style style(String source, int start, int end, int to, String name, Set<String> locals) {
        int before = start - 1;
        while (before >= 0 && source.charAt(before) == ' ') before--;
        if (before >= 0 && source.charAt(before) == '.' && (before == 0 || source.charAt(before - 1) != '.')) {
            return Style.MEMBER;
        }
        int after = end;
        while (after < to && source.charAt(after) == ' ') after++;
        if (after < to && source.charAt(after) == '(') return Style.FUNCTION;
        if (KEYWORDS.contains(name)) return Style.KEYWORD;
        if (BUILTINS.contains(name)) return Style.BUILTIN;
        if (locals.contains(name)) return Style.LOCAL;
        return Style.TRACK;
    }

    static boolean identifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    static int closing(String source, int from) {
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
}
