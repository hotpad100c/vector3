package ml.mypals.vectorthree.fb.expression;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.fb.channel.ChannelSpec;
import ml.mypals.vectorthree.fb.channel.Channels;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeChange;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeType;
import org.jetbrains.annotations.Nullable;

/** Turns a track's evaluated change back into a keyframe, so its editor can read or edit the evaluated value. */
final class KeyframeRebuild {
    private KeyframeRebuild() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static @Nullable Keyframe of(KeyframeTrack track, KeyframeChange change) {
        if (track.keyframeType instanceof CustomKeyframeType<?> type && change instanceof CustomKeyframeChange custom) {
            Keyframe keyframe = type.keyframeOf(custom.value());
            if (keyframe != null) return keyframe;
        }
        ChannelSpec spec = Channels.spec(track.keyframeType);
        if (spec == null || track.keyframesByTick.isEmpty()) return null;
        Object result = spec.result(change);
        return result == null ? null : spec.keyframe(result, track.keyframesByTick.firstEntry().getValue());
    }
}
