package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import ml.mypals.vectorthree.flashback.channel.ChannelMasks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;

// Saved through the keyframe JSON adapters (see ChannelMasks#write), not reflectively.
@Mixin(value = Keyframe.class, remap = false)
public class KeyframeChannelMixin implements ChannelMasks.Holder {
    @Unique private transient Set<String> vector3$channels;

    @Override public Set<String> vector3$channels() { return vector3$channels; }
    @Override public void vector3$setChannels(Set<String> channels) { vector3$channels = channels; }
}
