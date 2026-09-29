package ml.mypals.vectorthree.core.expression.lang;

import java.util.List;

/** {@code global}: the scene's global variables, read by name. */
record GlobalsRef(Scope scope) implements Value.Obj {
    @Override
    public Value member(String name) {
        return scope.global(name);
    }

    @Override
    public List<String> members() {
        return scope.globalNames();
    }

    @Override
    public String describe() {
        return "global";
    }
}
