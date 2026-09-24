package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.*;
import com.moulberry.flashback.keyframe.Keyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

/** Flashback's Keyframe adapter only knows its own types; save and load vector3's custom ones. */
@Mixin(Keyframe.TypeAdapter.class)
public class KeyframeTypeAdapterMixin {
    @Inject(method = "serialize(Lcom/moulberry/flashback/keyframe/Keyframe;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;", at = @At("HEAD"), cancellable = true, remap = false)
    private void vector3$serialize(Keyframe keyframe, Type type, JsonSerializationContext context,
            CallbackInfoReturnable<JsonElement> cir) {
        if (keyframe instanceof CustomKeyframe<?> custom) cir.setReturnValue(CustomKeyframes.write(custom, context));
    }

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/moulberry/flashback/keyframe/Keyframe;", at = @At("HEAD"), cancellable = true, remap = false)
    private void vector3$deserialize(JsonElement element, Type type, JsonDeserializationContext context,
            CallbackInfoReturnable<Keyframe> cir) {
        if (!element.isJsonObject()) return;
        JsonObject json = element.getAsJsonObject();
        if (!json.has("type")) return;
        CustomKeyframeType<?> custom = CustomKeyframes.byId(json.get("type").getAsString());
        if (custom != null) cir.setReturnValue(CustomKeyframes.read(custom, json, context));
    }
}
