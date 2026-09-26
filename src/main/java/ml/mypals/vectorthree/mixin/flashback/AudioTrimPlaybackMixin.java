package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.keyframe.change.KeyframeChangePlayAudio;
import com.moulberry.flashback.keyframe.impl.AudioKeyframe;
import com.moulberry.flashback.keyframe.types.AudioKeyframeType;
import ml.mypals.vectorthree.clips.AudioLevel;
import ml.mypals.vectorthree.clips.AudioTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AudioKeyframeType.class, remap = false)
public class AudioTrimPlaybackMixin {
    @WrapOperation(method = "customKeyframeChange", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/keyframe/impl/AudioKeyframe;createAudioChange(IF)Lcom/moulberry/flashback/keyframe/change/KeyframeChangePlayAudio;"))
    private KeyframeChangePlayAudio vector3$trimmedPlayback(AudioKeyframe keyframe, int startTick, float seconds,
            Operation<KeyframeChangePlayAudio> original) {
        AudioTrim trim = (AudioTrim) keyframe;
        AudioLevel level = (AudioLevel) keyframe;
        float media = seconds * level.vector3$pitch();
        if (trim.vector3$audioLength() >= 0 && media * 20 >= trim.vector3$audioLength()) return null;
        KeyframeChangePlayAudio change = original.call(keyframe, startTick, media + trim.vector3$audioIn() / 20f);
        if (change != null) ((AudioLevel) change).vector3$setLevel(level.vector3$volume(), level.vector3$pitch());
        return change;
    }
}
