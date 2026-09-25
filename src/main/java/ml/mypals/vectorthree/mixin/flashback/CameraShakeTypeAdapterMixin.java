package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.shake.ShakeHolder;
import ml.mypals.vectorthree.camera.shake.ShakeParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

@Mixin(targets = "com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe$TypeAdapter", remap = false)
public class CameraShakeTypeAdapterMixin {
    @Unique private static final String FIELD = "vector3_shake";
    @Unique private static final Gson GSON = new Gson();

    @Inject(method = "serialize(Lcom/moulberry/flashback/keyframe/impl/CameraShakeKeyframe;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;",
            at = @At("RETURN"))
    private void vector3$writeShake(CameraShakeKeyframe keyframe, Type type, JsonSerializationContext context,
            CallbackInfoReturnable<JsonElement> cir) {
        ShakeParams params = ((ShakeHolder) keyframe).vector3$shake();
        if (params != null && !params.equals(ShakeParams.DEFAULT) && cir.getReturnValue() instanceof JsonObject json) {
            json.add(FIELD, GSON.toJsonTree(params));
        }
    }

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/moulberry/flashback/keyframe/impl/CameraShakeKeyframe;",
            at = @At("RETURN"))
    private void vector3$readShake(JsonElement element, Type type, JsonDeserializationContext context,
            CallbackInfoReturnable<CameraShakeKeyframe> cir) {
        JsonObject json = element.getAsJsonObject();
        if (!json.has(FIELD)) return;
        try {
            ShakeParams params = GSON.fromJson(json.get(FIELD), ShakeParams.class);
            ((ShakeHolder) cir.getReturnValue()).vector3$setShake(ShakeParams.of(params.floats(), params.octaves(), params.seed()));
        } catch (RuntimeException exception) {
            Vector3.LOGGER.warn("Dropping unreadable camera shake settings", exception);
        }
    }
}
