package ml.mypals.vectorthree.core.port;

/** The sockets core code plugs into. The Flashback adapter installs the real implementations at startup. */
public final class Ports {
    private static volatile ReplayClock clock = ReplayClock.NONE;
    private static volatile EditorViewport viewport = EditorViewport.NONE;
    private static volatile ViewPolicy view = ViewPolicy.NONE;
    private static volatile ShapeEditing shapeEditing = ShapeEditing.NONE;
    private static volatile Splines splines;

    private Ports() {}

    public static void install(ReplayClock clock, EditorViewport viewport, ViewPolicy view, ShapeEditing shapeEditing, Splines splines) {
        Ports.clock = clock;
        Ports.viewport = viewport;
        Ports.view = view;
        Ports.shapeEditing = shapeEditing;
        Ports.splines = splines;
    }

    public static ReplayClock clock() {
        return clock;
    }

    public static EditorViewport viewport() {
        return viewport;
    }

    public static ViewPolicy view() {
        return view;
    }

    public static ShapeEditing shapeEditing() {
        return shapeEditing;
    }

    public static Splines splines() {
        return splines;
    }
}
