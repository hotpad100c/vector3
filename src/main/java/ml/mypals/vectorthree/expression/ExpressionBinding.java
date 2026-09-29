package ml.mypals.vectorthree.expression;

/**
 * One parameter of a track driven by an expression. {@code widget} is the editor widget's language-independent key
 * (see {@link WidgetKeys}); {@code component} picks one component of a multi-value widget, or -1 for all of them.
 */
public record ExpressionBinding(String widget, int component, String source, boolean enabled) {
    public static final int ALL = -1;

    public ExpressionBinding withSource(String source) {
        return new ExpressionBinding(widget, component, source, enabled);
    }

    public ExpressionBinding withEnabled(boolean enabled) {
        return new ExpressionBinding(widget, component, source, enabled);
    }

    public boolean targets(String widget, int component) {
        return this.widget.equals(widget) && this.component == component;
    }
}
