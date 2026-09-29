package ml.mypals.vectorthree.fb.channel;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import ml.mypals.vectorthree.fb.custom.CustomKeyframe;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeChange;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeType;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** Channels of one of vector3's own keyframe types, whose tracks evaluate to the keyframe value itself. */
public abstract class ValueChannels<T> implements ChannelSpec<T> {
    private final CustomKeyframeType<T> type;
    private final List<String> channels;

    protected ValueChannels(CustomKeyframeType<T> type, List<String> channels) {
        this.type = type;
        this.channels = channels;
    }

    @Override
    public List<String> channels(Collection<Keyframe> keyframes) {
        return channels;
    }

    @Override
    public @Nullable T result(KeyframeChange change) {
        if (!(change instanceof CustomKeyframeChange custom)) return null;
        Object value = custom.value();
        return type.valueType().isInstance(value) ? type.valueType().cast(value) : null;
    }

    @Override
    public KeyframeChange change(T value) {
        return type.changeOf(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Keyframe keyframe(T value, Keyframe template) {
        CustomKeyframe<T> keyframe = (CustomKeyframe<T>) template.copy();
        keyframe.value = value;
        return keyframe;
    }

    @Override
    public String label(String channel) {
        return I18n.get("vector3.channel." + channel);
    }
}
