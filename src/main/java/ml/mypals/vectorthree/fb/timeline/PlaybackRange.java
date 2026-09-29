package ml.mypals.vectorthree.fb.timeline;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;

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

    /**
     * Playback: once the playhead, playing inside the In/Out range, would pass Out, it goes back to In. Changing the
     * server's target tick (rather than skipping a replay tick) lets Flashback seek back like a click on the timeline.
     */
    public static int resolve(EditorState editorState, int current, int target) {
        long stamp = editorState.acquireRead();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            if (!enabled(scene)) return target;
            int in = in(scene), out = out(scene);
            if (in < 0 || out <= in || current < in || current > out || target <= out) return target;
            return in;
        } finally {
            editorState.release(stamp);
        }
    }
}
