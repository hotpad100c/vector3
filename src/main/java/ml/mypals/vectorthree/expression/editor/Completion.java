package ml.mypals.vectorthree.expression.editor;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * What is being typed at the cursor: the partial word, and the expression before a '.' when it is a member. In the
 * first argument of track( or entity( it is the name being typed instead ({@code argumentOf}), inside a string that
 * opened with {@code quote}, or 0 when no quote was typed yet.
 */
public final class Completion {
    public record Context(int wordStart, String prefix, @Nullable String target, @Nullable String argumentOf, char quote) {
        public Context(int wordStart, String prefix, @Nullable String target) {
            this(wordStart, prefix, target, null, (char) 0);
        }
    }

    /** Functions whose first argument names something that exists: a track, an entity. */
    public static final Set<String> NAME_ARGUMENTS = Set.of("track", "entity");

    private static final int COMMENT = -2;

    private Completion() {}

    /** Null when the cursor is somewhere nothing can be completed (a template's plain text, a string, a comment). */
    public static @Nullable Context at(String source, int cursor, boolean template) {
        cursor = Math.clamp(cursor, 0, source.length());
        if (template && !insideBraces(source, cursor)) return null;
        int open = openString(source, cursor, template);
        if (open == COMMENT) return null;
        if (open >= 0) {
            String function = functionBefore(source, open);
            return function == null ? null
                    : new Context(open + 1, source.substring(open + 1, cursor), null, function, source.charAt(open));
        }
        int start = cursor;
        while (start > 0 && Highlighter.identifierPart(source.charAt(start - 1))) start--;
        String prefix = source.substring(start, cursor);
        if (!prefix.isEmpty() && Character.isDigit(prefix.charAt(0))) return null;
        String target = null;
        if (start > 0 && source.charAt(start - 1) == '.') {
            target = chainEndingAt(source, start - 1);
            if (target == null || target.isEmpty()) return null;
        } else if (prefix.isEmpty()) {
            String function = functionBefore(source, start);
            if (function != null) return new Context(start, "", null, function, (char) 0);
        }
        return new Context(start, prefix, target);
    }

    /** The name-taking function whose '(' comes right before {@code index} (spaces aside), or null. */
    private static @Nullable String functionBefore(String source, int index) {
        int i = index;
        while (i > 0 && source.charAt(i - 1) == ' ') i--;
        if (i == 0 || source.charAt(i - 1) != '(') return null;
        i--;
        while (i > 0 && source.charAt(i - 1) == ' ') i--;
        int end = i;
        while (i > 0 && Highlighter.identifierPart(source.charAt(i - 1))) i--;
        String name = source.substring(i, end);
        boolean member = i > 0 && source.charAt(i - 1) == '.';
        return !member && NAME_ARGUMENTS.contains(name) ? name : null;
    }

    /** The member chain that ends right before {@code end}, e.g. entity("a").nbt for "entity("a").nbt|". */
    public static @Nullable String chainEndingAt(String source, int end) {
        int i = end;
        while (true) {
            while (i > 0 && source.charAt(i - 1) == ' ') i--;
            while (i > 0 && (source.charAt(i - 1) == ')' || source.charAt(i - 1) == ']')) {
                int open = matching(source, i - 1);
                if (open < 0) return null;
                i = open;
            }
            while (i > 0 && Highlighter.identifierPart(source.charAt(i - 1))) i--;
            if (i > 0 && source.charAt(i - 1) == '.') {
                i--;
                continue;
            }
            String chain = source.substring(i, end).trim();
            return chain.isEmpty() ? null : chain;
        }
    }

    private static int matching(String source, int close) {
        char closing = source.charAt(close), opening = closing == ')' ? '(' : '[';
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i - 1;
                while (j >= 0 && source.charAt(j) != c) j--;
                if (j < 0) return -1;
                i = j;
            } else if (c == closing) {
                depth++;
            } else if (c == opening && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    public static boolean insideBraces(String source, int cursor) {
        boolean open = false;
        for (int i = 0; i < cursor; i++) {
            char c = source.charAt(i);
            if (!open && (c == '{' || c == '}') && i + 1 < source.length() && source.charAt(i + 1) == c) {
                i++;
            } else if (!open && c == '{') {
                open = true;
            } else if (open) {
                int end = Highlighter.closing(source, i);
                if (end < 0 || end >= cursor) return true;
                i = end;
                open = false;
            }
        }
        return open;
    }

    /** Where the string the cursor is in opened, COMMENT inside a comment, or -1. */
    private static int openString(String source, int cursor, boolean template) {
        char quote = 0;
        int opened = -1;
        boolean comment = false;
        boolean code = !template;
        for (int i = 0; i < cursor; i++) {
            char c = source.charAt(i);
            if (template && quote == 0 && !comment) {
                if (!code && c == '{' && !(i + 1 < source.length() && source.charAt(i + 1) == '{')) code = true;
                else if (code && c == '}') code = false;
                if (!code) continue;
            }
            if (comment) {
                if (c == '\n') comment = false;
            } else if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                comment = true;
            } else if (c == '"' || c == '\'') {
                quote = c;
                opened = i;
            }
        }
        return comment ? COMMENT : quote != 0 ? opened : -1;
    }
}
