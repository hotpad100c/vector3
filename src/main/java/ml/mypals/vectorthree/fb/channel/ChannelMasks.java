package ml.mypals.vectorthree.fb.channel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moulberry.flashback.keyframe.Keyframe;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which channels a keyframe keys. Kept on every Keyframe (KeyframeChannelMixin); null means all of them, which is
 * what every keyframe from before this feature (or on a track without it) is.
 */
public final class ChannelMasks {
    public interface Holder {
        @Nullable Set<String> vector3$channels();

        void vector3$setChannels(@Nullable Set<String> channels);
    }

    private static final String JSON_KEY = "vector3_channels";

    private ChannelMasks() {}

    public static @Nullable Set<String> of(Keyframe keyframe) {
        return ((Holder) keyframe).vector3$channels();
    }

    public static void set(Keyframe keyframe, @Nullable Set<String> channels) {
        ((Holder) keyframe).vector3$setChannels(channels == null ? null : Set.copyOf(channels));
    }

    public static boolean keys(Keyframe keyframe, String channel) {
        Set<String> channels = of(keyframe);
        return channels == null || channels.contains(channel);
    }

    /** The keyframe's mask with {@code channel} on or off; a full mask is listed out first. */
    public static Set<String> with(Keyframe keyframe, List<String> all, String channel, boolean keyed) {
        Set<String> channels = new LinkedHashSet<>(of(keyframe) == null ? all : of(keyframe));
        if (keyed) channels.add(channel);
        else channels.remove(channel);
        return channels;
    }

    /** Copies {@code from}'s mask onto {@code to} and keys whatever channels the edit changed. */
    public static void carry(Keyframe from, Keyframe to) {
        Set<String> channels = of(from);
        set(to, channels);
        if (channels != null) markChanged(from, to);
    }

    /** Keys the channels that differ between {@code before} and {@code after} on {@code after}, if it has a mask. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void markChanged(Keyframe before, Keyframe after) {
        Set<String> channels = of(after);
        if (channels == null) return;
        ChannelSpec spec = Channels.spec(after.keyframeType());
        if (spec == null) return;
        Object a = spec.result(before), b = spec.result(after);
        if (a == null || b == null) return;
        Collection<String> all = spec.channels(List.of(before, after));
        Set<String> changed = spec.changed(a, b, all);
        if (changed.isEmpty() || channels.containsAll(changed)) return;
        Set<String> next = new LinkedHashSet<>(channels);
        next.addAll(changed);
        set(after, next);
    }

    public static void write(Keyframe keyframe, JsonObject json) {
        Set<String> channels = of(keyframe);
        if (channels == null) return;
        JsonArray array = new JsonArray();
        channels.forEach(array::add);
        json.add(JSON_KEY, array);
    }

    public static void read(Keyframe keyframe, JsonObject json) {
        if (!json.has(JSON_KEY) || !json.get(JSON_KEY).isJsonArray()) return;
        Set<String> channels = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray(JSON_KEY)) channels.add(element.getAsString());
        set(keyframe, channels);
    }
}
