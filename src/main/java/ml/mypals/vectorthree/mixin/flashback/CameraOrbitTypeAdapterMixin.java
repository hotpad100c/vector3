package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

@Mixin(targets = "com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe$TypeAdapter", remap = false)
public class CameraOrbitTypeAdapterMixin {
    @Unique private static final String TILT_X = "vector3_tilt_x";
    @Unique private static final String TILT_Z = "vector3_tilt_z";

    @Inject(method = "serialize(Lcom/moulberry/flashback/keyframe/impl/CameraOrbitKeyframe;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;",
            at = @At("RETURN"))
    private void vector3$writeTilt(CameraOrbitKeyframe keyframe, Type type, JsonSerializationContext context,
            CallbackInfoReturnable<JsonElement> cir) {
        OrbitTilt tilt = (OrbitTilt) keyframe;
        if (tilt.vector3$tiltX() == 0 && tilt.vector3$tiltZ() == 0) return;
        JsonObject json = cir.getReturnValue().getAsJsonObject();
        json.addProperty(TILT_X, tilt.vector3$tiltX());
        json.addProperty(TILT_Z, tilt.vector3$tiltZ());
    }

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/moulberry/flashback/keyframe/impl/CameraOrbitKeyframe;",
            at = @At("RETURN"))
    private void vector3$readTilt(JsonElement element, Type type, JsonDeserializationContext context,
            CallbackInfoReturnable<CameraOrbitKeyframe> cir) {
        JsonObject json = element.getAsJsonObject();
        if (!json.has(TILT_X) && !json.has(TILT_Z)) return;
        ((OrbitTilt) cir.getReturnValue()).vector3$setTilt(
                json.has(TILT_X) ? json.get(TILT_X).getAsFloat() : 0,
                json.has(TILT_Z) ? json.get(TILT_Z).getAsFloat() : 0);
    }
}
