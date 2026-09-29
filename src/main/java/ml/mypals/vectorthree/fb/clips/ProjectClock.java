package ml.mypals.vectorthree.fb.clips;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.core.clips.ClipRef;
import ml.mypals.vectorthree.core.clips.TimeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The project clock of a replay with a Clips track: the timeline runs in project ticks, and the replay server, which
 * only knows the archive's own ticks, is pointed at {@link TimeMap#physical} of the project tick each server tick.
 * Without composed clips the map is empty and both clocks are the same.
 */
public final class ProjectClock {
    private static volatile TimeMap map = TimeMap.EMPTY;
    private static volatile int tick;
    private static volatile int lastTick;
    private static Object server;

    private ProjectClock() {}

    public static boolean active() {
        return !map.isEmpty();
    }

    public static TimeMap map() {
        return map;
    }

    public static int tick() {
        return tick;
    }

    public static int lastTick() {
        return lastTick;
    }

    public static void set(int project) {
        tick = project;
    }

    private static volatile boolean playing;

    /** Whether the replay was playing (not paused) as this server tick began. */
    public static boolean playing() {
        return playing;
    }

    public static void beginTick(boolean isPlaying) {
        lastTick = tick;
        playing = isPlaying;
    }

    /** True the first time a server is seen, so the clock can start from where that server is. */
    public static boolean isNewServer(Object current) {
        if (server == current) return false;
        server = current;
        return true;
    }

    /** Rebuilds the map from the composed clips of the scene on the timeline. */
    public static void refresh(EditorScene scene) {
        KeyframeTrack track = scene == null ? null : ClipProject.clipTrack(scene);
        TimeMap next = TimeMap.EMPTY;
        if (track != null) {
            List<TimeMap.Segment> segments = new ArrayList<>();
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                if (!(entry.getValue() instanceof ClipKeyframeType.ClipKeyframe clip)) continue;
                ClipRef ref = clip.value;
                if (ref.composed()) segments.add(new TimeMap.Segment(entry.getKey(), ref.length(), ref.visibleStart()));
            }
            next = TimeMap.of(segments);
        }
        map = next;
    }
}
