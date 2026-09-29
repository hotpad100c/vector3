package ml.mypals.vectorthree.core.expression.editor;

/** A completion: what goes into the code, what the list shows (and matches), and a note beside it. */
public record Suggestion(String insert, String label, String detail) {}
