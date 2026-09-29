package ml.mypals.vectorthree.fb.channel;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import ml.mypals.vectorthree.fb.timeline.TrackManagement;
import ml.mypals.vectorthree.fb.loop.TrackRepeat;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-channel keyframing. On a track switched to it, each channel is evaluated over only the keyframes that key it,
 * through the track's normal evaluation (interpolation types, smooth neighbours, speed curves and repeat all apply
 * per channel for free), and the results are put together. Before a channel's first keyframe and after its last, the
 * channel holds that keyframe's value.
 */
public final class Channels {
    /** Implemented on KeyframeTrack. */
    public interface TrackHolder {
        boolean vector3$perChannel();

        void vector3$setPerChannel(boolean perChannel);
    }

    private static final Map<KeyframeType<?>, ChannelSpec<?>> SPECS = new HashMap<>();
    private static final ThreadLocal<Boolean> EVALUATING = ThreadLocal.withInitial(() -> false);

    private Channels() {}

    public static void register(KeyframeType<?> type, ChannelSpec<?> spec) {
        SPECS.put(type, spec);
    }

    public static @Nullable ChannelSpec<?> spec(KeyframeType<?> type) {
        return SPECS.get(type);
    }

    public static boolean enabled(KeyframeTrack track) {
        return ((TrackHolder) track).vector3$perChannel() && SPECS.containsKey(track.keyframeType);
    }

    public static void setEnabled(KeyframeTrack track, boolean enabled) {
        ((TrackHolder) track).vector3$setPerChannel(enabled);
    }

    /** Whether the track needs the per-channel path this call; otherwise it evaluates as usual. */
    public static boolean handles(KeyframeTrack track) {
        if (EVALUATING.get() || !enabled(track)) return false;
        for (Keyframe keyframe : track.keyframesByTick.values()) {
            if (ChannelMasks.of(keyframe) != null) return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable KeyframeChange evaluate(KeyframeTrack track, float tick, @Nullable RealTimeMapping mapping) {
        ChannelSpec spec = SPECS.get(track.keyframeType);
        EVALUATING.set(true);
        try {
            KeyframeChange full = track.createKeyframeChange(tick, mapping);
            if (full == null) return null;
            Object base = spec.result(full);
            if (base == null) return full;
            // The track's own repeat decides where in the cycle every channel is.
            float at = TrackRepeat.of(track).remap(track.keyframesByTick, tick);
            List<String> channels = spec.channels(track.keyframesByTick.values());
            Map<String, Object> byChannel = new HashMap<>();
            for (String channel : channels) {
                TreeMap<Integer, Keyframe> keyed = new TreeMap<>();
                track.keyframesByTick.forEach((key, keyframe) -> {
                    if (ChannelMasks.keys(keyframe, channel)) keyed.put(key, keyframe);
                });
                if (keyed.isEmpty() || keyed.size() == track.keyframesByTick.size()) continue;
                Object result = evaluateKeyed(track, keyed, at, mapping, spec);
                if (result != null) byChannel.put(channel, result);
            }
            return byChannel.isEmpty() ? full : spec.change(spec.compose(base, byChannel));
        } finally {
            EVALUATING.set(false);
        }
    }

    @SuppressWarnings("rawtypes")
    private static @Nullable Object evaluateKeyed(KeyframeTrack track, TreeMap<Integer, Keyframe> keyed, float tick,
            @Nullable RealTimeMapping mapping, ChannelSpec spec) {
        KeyframeTrack channelTrack = new KeyframeTrack(track.keyframeType);
        channelTrack.keyframesByTick = keyed;
        channelTrack.enabled = true;
        ((TrackManagement.Holder) channelTrack).vector3$setSolo(((TrackManagement.Holder) track).vector3$solo());
        KeyframeChange change = channelTrack.createKeyframeChange(tick, mapping);
        if (change == null) {
            // Outside the channel's own keyframes it holds the nearest one.
            Map.Entry<Integer, Keyframe> held = tick < keyed.firstKey() ? keyed.firstEntry() : keyed.floorEntry((int) tick);
            if (held == null) held = keyed.lastEntry();
            change = held.getValue().createChange();
        }
        return spec.result(change);
    }
}
