package ml.mypals.vectorthree.fb.channel;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How one keyframe type splits into separately keyed properties ("channels"). {@code R} is what a track evaluates
 * to: the value for vector3's own types, the KeyframeChange for Flashback's.
 */
public interface ChannelSpec<R> {
    /** The channels of a track holding these keyframes; may depend on them (e.g. an entity's parts). */
    List<String> channels(Collection<Keyframe> keyframes);

    @Nullable R result(KeyframeChange change);

    KeyframeChange change(R value);

    /** {@code base} with each channel of {@code byChannel} taken from its own result. */
    R compose(R base, Map<String, R> byChannel);

    /** Whether {@code a} and {@code b} agree on {@code channel}. */
    boolean same(R a, R b, String channel);

    String label(String channel);

    /**
     * A keyframe like {@code template} (same interpolation, group, ...) holding {@code value}, or null when this type
     * can't be rebuilt from its evaluated value.
     */
    default @Nullable Keyframe keyframe(R value, Keyframe template) {
        return null;
    }

    /** Translation keys of the panel labels that edit each channel, for placing its keying buttons. */
    default Map<String, String> labels() {
        return Map.of();
    }

    /** The channel a panel row edits, from the row's visible label, or null. */
    default @Nullable String channelOfLabel(String label) {
        for (Map.Entry<String, String> entry : labels().entrySet()) {
            if (net.minecraft.client.resources.language.I18n.get(entry.getKey()).equals(label)) return entry.getValue();
        }
        return null;
    }

    default @Nullable R result(Keyframe keyframe) {
        return result(keyframe.createChange());
    }

    default Set<String> changed(R before, R after, Collection<String> channels) {
        Set<String> changed = new LinkedHashSet<>();
        for (String channel : channels) if (!same(before, after, channel)) changed.add(channel);
        return changed;
    }
}
