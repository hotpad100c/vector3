package ml.mypals.vectorthree.expression;

/** A named expression shared by the whole scene, read from other expressions as global.name. */
public record GlobalVariable(String name, String source) {
    public GlobalVariable withName(String name) {
        return new GlobalVariable(name, source);
    }

    public GlobalVariable withSource(String source) {
        return new GlobalVariable(name, source);
    }
}
