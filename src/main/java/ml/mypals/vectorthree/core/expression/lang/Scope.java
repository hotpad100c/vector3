package ml.mypals.vectorthree.core.expression.lang;

import java.util.List;

/** Where an expression runs: the time, the parameter's own keyframed value, and the other things it can read. */
public interface Scope {
    double tick();

    Value value();

    Value track(String name);

    Value entity(String id);

    Value camera();

    /** The parameters of the track being driven, before its expressions. */
    default Value self() {
        throw new ExprError("self is not available here");
    }

    default Value global(String name) {
        throw new ExprError("there are no global variables here");
    }

    default List<String> globalNames() {
        return List.of();
    }

    /** Called when a let binds {@code name}, so editors can show what each local held. */
    default void let(String name, Value value) {
    }

    /** A scope with only a time, for tests and previews. */
    static Scope ofTick(double tick) {
        return new Scope() {
            @Override public double tick() { return tick; }
            @Override public Value value() { throw new ExprError("value is not available here"); }
            @Override public Value track(String name) { throw new ExprError("no track \"" + name + "\""); }
            @Override public Value entity(String id) { throw new ExprError("no entity \"" + id + "\""); }
            @Override public Value camera() { throw new ExprError("camera is not available here"); }
        };
    }
}
