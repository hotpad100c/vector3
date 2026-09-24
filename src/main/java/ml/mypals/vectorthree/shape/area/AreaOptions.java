package ml.mypals.vectorthree.shape.area;

public record AreaOptions(boolean projectEntities, boolean projectParticles) {
    public static final AreaOptions DEFAULT = new AreaOptions(false, false);

    public static AreaOptions orDefault(AreaOptions options) {
        return options == null ? DEFAULT : options;
    }

    public static AreaOptions transition(AreaOptions from, AreaOptions to, double amount) {
        if (from == null && to == null) return null;
        return amount < 0.5 ? orDefault(from) : orDefault(to);
    }
}
