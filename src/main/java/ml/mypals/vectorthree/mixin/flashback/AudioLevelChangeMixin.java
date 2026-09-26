package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.keyframe.change.KeyframeChangePlayAudio;
import com.moulberry.flashback.sound.FlashbackAudioBuffer;
import ml.mypals.vectorthree.clips.AudioLevel;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = KeyframeChangePlayAudio.class, remap = false)
public class AudioLevelChangeMixin implements AudioLevel {
    @Unique private float vector3$volume = 1;
    @Unique private float vector3$pitch = 1;

    @Override public float vector3$volume() { return vector3$volume; }
    @Override public float vector3$pitch() { return vector3$pitch; }

    @Override
    public void vector3$setLevel(float volume, float pitch) {
        vector3$volume = volume;
        vector3$pitch = pitch;
    }

    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/sound/FlashbackAudioManager;playAt(Lnet/minecraft/client/sounds/SoundEngine;Lcom/moulberry/flashback/sound/FlashbackAudioBuffer;IFF)V"))
    private void vector3$playLevelled(SoundEngine engine, FlashbackAudioBuffer buffer, int startTick, float seconds, float speed,
            Operation<Void> original) {
        Playing.volume = vector3$volume;
        try {
            original.call(engine, buffer, startTick, seconds, speed * vector3$pitch);
        } finally {
            Playing.volume = 1;
        }
    }
}
