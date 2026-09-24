package ml.mypals.vectorthree.shape;

public record WireframeSettings(boolean hideFaces, boolean enabled, boolean separateColor, int color) {
    public static final WireframeSettings DEFAULT = new WireframeSettings(false, false, false, 0xFFFFFFFF);

    public static WireframeSettings orDefault(WireframeSettings settings) {
        return settings == null ? DEFAULT : settings;
    }

    public static WireframeSettings transition(WireframeSettings from, WireframeSettings to, double amount) {
        if (from == null && to == null) return null;
        from = orDefault(from);
        to = orDefault(to);
        WireframeSettings toggles = amount < 0.5 ? from : to;
        return new WireframeSettings(toggles.hideFaces, toggles.enabled, toggles.separateColor,
                ShapeState.lerpColor(from.color, to.color, amount));
    }
}
