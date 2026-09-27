package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.state.EditorScene;

public final class PlaybackRange {
    public interface Holder {
        boolean vector3$loopPlayback();
        void vector3$setLoopPlayback(boolean enabled);
    }

    private PlaybackRange() {}
    public static int in(EditorScene scene) { return scene.exportStartTicks; }
    public static int out(EditorScene scene) { return scene.exportEndTicks; }
    public static boolean enabled(EditorScene scene) { return ((Holder) scene).vector3$loopPlayback(); }
    public static void setEnabled(EditorScene scene, boolean enabled) {
        ((Holder) scene).vector3$setLoopPlayback(enabled);
    }
}
