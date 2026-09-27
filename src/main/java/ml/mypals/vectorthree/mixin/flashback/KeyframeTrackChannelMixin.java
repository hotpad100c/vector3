package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import ml.mypals.vectorthree.flashback.channel.Channels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The mode is saved with the track by Flashback's reflective Gson, like the repeat mode.
@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackChannelMixin implements Channels.TrackHolder {
    @Unique private boolean vector3$perChannel;

    @Override public boolean vector3$perChannel() { return vector3$perChannel; }
    @Override public void vector3$setPerChannel(boolean perChannel) { vector3$perChannel = perChannel; }

    @Inject(method = "createKeyframeChange", at = @At("HEAD"), cancellable = true)
    private void vector3$perChannel(float tick, RealTimeMapping mapping, CallbackInfoReturnable<KeyframeChange> cir) {
        KeyframeTrack track = (KeyframeTrack) (Object) this;
        if (Channels.handles(track)) cir.setReturnValue(Channels.evaluate(track, tick, mapping));
    }
}
