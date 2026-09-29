package ml.mypals.vectorthree.core.port;

/** What core code needs to know about replay playback and export. Implemented on the Flashback side. */
public interface ReplayClock {
    boolean replayLoaded();

    boolean paused();

    boolean exporting();

    boolean exportTransparent();

    /** Seconds per exported frame, or 0 when not exporting. */
    double exportFrameSeconds();

    double exportTick();

    /** Playback position with the sub-tick fraction. */
    double partialTick();

    /** Where the timeline cursor sits. */
    double cursorTick();

    /** The tick a time-driven effect should show now: the export tick, the playback position, or the cursor while paused. */
    default double effectTick() {
        if (exporting()) return exportTick();
        return replayLoaded() && !paused() ? partialTick() : cursorTick();
    }

    ReplayClock NONE = new ReplayClock() {
        public boolean replayLoaded() { return false; }
        public boolean paused() { return true; }
        public boolean exporting() { return false; }
        public boolean exportTransparent() { return false; }
        public double exportFrameSeconds() { return 0; }
        public double exportTick() { return 0; }
        public double partialTick() { return 0; }
        public double cursorTick() { return 0; }
    };
}
