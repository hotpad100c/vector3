package ml.mypals.vectorthree.core.port;

/** The editor's game view, as core code sees it. Implemented on the Flashback side. */
public interface EditorViewport {
    /** The editor UI is up and the view is showing. */
    boolean active();

    /** Mouse position over the view, 0..1 from the top-left. */
    double mouseFractionX();

    double mouseFractionY();

    int heightPixels();

    EditorViewport NONE = new EditorViewport() {
        public boolean active() { return false; }
        public double mouseFractionX() { return 0.5; }
        public double mouseFractionY() { return 0.5; }
        public int heightPixels() { return 1; }
    };
}
