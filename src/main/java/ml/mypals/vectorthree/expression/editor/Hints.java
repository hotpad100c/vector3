package ml.mypals.vectorthree.expression.editor;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** What the code editor asks about the code it shows, answered by evaluating it where it runs. */
public interface Hints {
    /** Member names of what {@code target} evaluates to; methods end with "()". */
    List<String> members(String target);

    /** "type = value" of an expression, for hovering; null when it can't be evaluated. */
    @Nullable String describe(String expression);

    /** Names an expression can start with here besides the built-ins, e.g. track names. */
    List<String> names();

    /**
     * What can go in the first argument of {@code function} (track, entity): the value to insert, unquoted, with a
     * label to show and match against.
     */
    List<CodeEditor.Suggestion> arguments(String function);
}
