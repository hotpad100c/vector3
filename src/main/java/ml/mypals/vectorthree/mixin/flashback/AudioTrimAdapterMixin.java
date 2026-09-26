package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.moulberry.flashback.keyframe.impl.AudioKeyframe;
import ml.mypals.vectorthree.clips.AudioLevel;
import ml.mypals.vectorthree.clips.AudioTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

@Mixin(targets = "com.moulberry.flashback.keyframe.impl.AudioKeyframe$TypeAdapter", remap = false)
public class AudioTrimAdapterMixin {
    @Unique private static final String IN = "vector3_audio_in";
    @Unique private static final String LENGTH = "vector3_audio_length";
    @Unique private static final String VOLUME = "vector3_audio_volume";
    @Unique private static final String PITCH = "vector3_audio_pitch";

    @Inject(method = "serialize(Lcom/moulberry/flashback/keyframe/impl/AudioKeyframe;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;",
            at = @At("RETURN"))
    private void vector3$writeTrim(AudioKeyframe keyframe, Type type, JsonSerializationContext context,
            CallbackInfoReturnable<JsonElement> cir) {
        if (!(cir.getReturnValue() instanceof JsonObject json)) return;
        AudioTrim trim = (AudioTrim) keyframe;
        AudioLevel level = (AudioLevel) keyframe;
        if (trim.vector3$audioIn() != 0 || trim.vector3$audioLength() >= 0) {
            json.addProperty(IN, trim.vector3$audioIn());
            json.addProperty(LENGTH, trim.vector3$audioLength());
        }
        if (level.vector3$volume() != 1) json.addProperty(VOLUME, level.vector3$volume());
        if (level.vector3$pitch() != 1) json.addProperty(PITCH, level.vector3$pitch());
    }

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/moulberry/flashback/keyframe/impl/AudioKeyframe;",
            at = @At("RETURN"))
    private void vector3$readTrim(JsonElement element, Type type, JsonDeserializationContext context,
            CallbackInfoReturnable<AudioKeyframe> cir) {
        JsonObject json = element.getAsJsonObject();
        if (cir.getReturnValue() == null) return;
        if (json.has(IN) || json.has(LENGTH)) {
            ((AudioTrim) cir.getReturnValue()).vector3$setAudioTrim(json.has(IN) ? json.get(IN).getAsInt() : 0,
                    json.has(LENGTH) ? json.get(LENGTH).getAsInt() : -1);
        }
        if (json.has(VOLUME) || json.has(PITCH)) {
            ((AudioLevel) cir.getReturnValue()).vector3$setLevel(json.has(VOLUME) ? json.get(VOLUME).getAsFloat() : 1,
                    json.has(PITCH) ? json.get(PITCH).getAsFloat() : 1);
        }
    }
}
