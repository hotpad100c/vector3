package ml.mypals.vectorthree.core.expression.lang;

/** A parse or evaluation error; {@code position} is the character offset in the source, or -1. */
public final class ExprError extends RuntimeException {
    private final int position;

    public ExprError(String message) {
        this(message, -1);
    }

    public ExprError(String message, int position) {
        super(message, null, false, false);
        this.position = position;
    }

    public int position() {
        return position;
    }

    public ExprError at(int position) {
        return this.position >= 0 ? this : new ExprError(getMessage(), position);
    }

    public ExprError shifted(int offset) {
        return position < 0 ? this : new ExprError(getMessage(), position + offset);
    }
}
