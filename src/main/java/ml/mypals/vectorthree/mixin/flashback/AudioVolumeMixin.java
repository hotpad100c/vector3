package ml.mypals.vectorthree.mixin.flashback;

import com.mojang.blaze3d.audio.Channel;
import com.moulberry.flashback.sound.FlashbackAudioManager;
import ml.mypals.vectorthree.clips.AudioLevel;
import ml.mypals.vectorthree.mixin.ChannelSourceAccessor;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;
@Mixin(value = FlashbackAudioManager.class, remap = false)
public class AudioVolumeMixin {
    @ModifyArg(method = "playAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private static Consumer<Channel> vector3$withVolume(Consumer<Channel> action) {
        float volume = AudioLevel.Playing.volume;
        return channel -> {
            action.accept(channel);
            AL10.alSourcef(((ChannelSourceAccessor) channel).vector3$source(), AL10.AL_MAX_GAIN, Math.max(1, volume));
            channel.setVolume(volume);
        };
    }
}
