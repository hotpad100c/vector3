package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.flashback.loop.TrackRepeat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.TreeMap;

// Saved with the track by Flashback's reflective Gson, like the prefab group field.
@Mixin(value = KeyframeTrack.class, remap = false)
public class KeyframeTrackRepeatMixin implements TrackRepeat.Holder {
    @Shadow public TreeMap<Integer, Keyframe> keyframesByTick;
    @Unique private TrackRepeat vector3$repeat;

    @Override public TrackRepeat vector3$repeat() { return vector3$repeat; }
    @Override public void vector3$setRepeat(TrackRepeat repeat) { vector3$repeat = repeat; }

    @ModifyVariable(method = "createKeyframeChange", at = @At("HEAD"), argsOnly = true)
    private float vector3$repeatTick(float tick) {
        return vector3$repeat == null ? tick : vector3$repeat.remap(keyframesByTick, tick);
    }
}
