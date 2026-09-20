package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.GsonBuilder;
import com.moulberry.flashback.FlashbackGson;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframeSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlashbackGson.class)
public class FlashbackGsonMixin {
    @Inject(method = "build", at = @At("RETURN"), remap = false)
    private static void vector3$registerShapeAdapter(CallbackInfoReturnable<GsonBuilder> cir) {
        cir.getReturnValue().registerTypeAdapter(ShapeKeyframe.class, ShapeKeyframeSerializer.INSTANCE);
    }
}
