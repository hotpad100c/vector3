package ml.mypals.vectorthree.expression.lang;

import java.util.List;

/** One evaluation of a program: its let locals, around the scope it runs in. */
final class Frame implements Scope {
    private final Scope outer;
    final Value[] locals;

    Frame(Scope outer, int size) {
        this.outer = outer;
        this.locals = new Value[size];
    }

    static Value[] locals(Scope scope) {
        return ((Frame) scope).locals;
    }

    @Override public double tick() { return outer.tick(); }
    @Override public Value value() { return outer.value(); }
    @Override public Value track(String name) { return outer.track(name); }
    @Override public Value entity(String id) { return outer.entity(id); }
    @Override public Value camera() { return outer.camera(); }
    @Override public Value self() { return outer.self(); }
    @Override public Value global(String name) { return outer.global(name); }
    @Override public List<String> globalNames() { return outer.globalNames(); }
    @Override public void let(String name, Value value) { outer.let(name, value); }
}
