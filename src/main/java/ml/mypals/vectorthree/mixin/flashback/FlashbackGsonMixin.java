package ml.mypals.vectorthree.mixin.flashback;

import com.google.gson.GsonBuilder;
import com.moulberry.flashback.FlashbackGson;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlashbackGson.class)
public class FlashbackGsonMixin {
    // A hierarchy adapter, so CustomKeyframe subclasses such as ShapeKeyframe are covered too.
    @Inject(method = "build", at = @At("RETURN"), remap = false)
    private static void vector3$registerCustomKeyframeAdapter(CallbackInfoReturnable<GsonBuilder> cir) {
        cir.getReturnValue().registerTypeHierarchyAdapter(CustomKeyframe.class, CustomKeyframes.SERIALIZER);
    }
}
