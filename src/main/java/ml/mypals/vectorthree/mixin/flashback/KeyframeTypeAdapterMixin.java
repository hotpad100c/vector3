package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.*;
import com.moulberry.flashback.keyframe.Keyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframeSerializer;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

@Mixin(Keyframe.TypeAdapter.class)
public class KeyframeTypeAdapterMixin {
    @Inject(method = "serialize(Lcom/moulberry/flashback/keyframe/Keyframe;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;", at = @At("HEAD"), cancellable = true, remap = false)
    private void vector3$serialize(Keyframe keyframe, Type type, JsonSerializationContext context,
            CallbackInfoReturnable<JsonElement> cir) {
        if (keyframe instanceof ShapeKeyframe shape) cir.setReturnValue(ShapeKeyframeSerializer.write(shape, context));
    }

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/moulberry/flashback/keyframe/Keyframe;", at = @At("HEAD"), cancellable = true, remap = false)
    private void vector3$deserialize(JsonElement element, Type type, JsonDeserializationContext context,
            CallbackInfoReturnable<Keyframe> cir) {
        if (!element.isJsonObject()) return;
        JsonObject json = element.getAsJsonObject();
        if (json.has("type") && ShapeKeyframeType.ID.equals(json.get("type").getAsString())) {
            cir.setReturnValue(ShapeKeyframeSerializer.read(json, context));
        }
    }
}
