package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;

import java.util.List;
import java.util.TreeMap;

public record Prefab(String name, List<Track> tracks) {
    public record Track(KeyframeType<?> type, String customName, int customColour, TreeMap<Integer, Keyframe> keyframes) {}

    public int duration() {
        int duration = 0;
        for (Track track : tracks) {
            if (!track.keyframes().isEmpty()) duration = Math.max(duration, track.keyframes().lastKey());
        }
        return duration;
    }
}
