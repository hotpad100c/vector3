package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.moulberry.flashback.keyframe.Keyframe;
import ml.mypals.vectorthree.flashback.curve.SpeedCurves;
import ml.mypals.vectorthree.prefab.PrefabGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

@Mixin(targets = {
        "com.moulberry.flashback.keyframe.impl.CameraKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.FOVKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.TickrateKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.TimelapseKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.FreezeKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.BlockOverrideKeyframe$TypeAdapter",
        "com.moulberry.flashback.keyframe.impl.AudioKeyframe$TypeAdapter"}, remap = false)
public class KeyframeSubtypeAdapterMixin {
    @Inject(method = "serialize(Ljava/lang/Object;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;",
            at = @At("RETURN"))
    private void vector3$writeExtras(Object keyframe, Type type, JsonSerializationContext context,
            CallbackInfoReturnable<JsonElement> cir) {
        if (keyframe instanceof Keyframe k && cir.getReturnValue() instanceof JsonObject json) {
            PrefabGroups.writeGroup(k, json);
            SpeedCurves.write(k, json);
        }
    }
}
