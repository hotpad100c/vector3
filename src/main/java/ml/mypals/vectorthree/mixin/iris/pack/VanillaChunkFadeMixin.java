package ml.mypals.vectorthree.mixin.iris.pack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = {
        "net.irisshaders.iris.pipeline.transform.transformer.VanillaTransformer",
        "net.irisshaders.iris.pipeline.transform.transformer.VanillaCoreTransformer"}, remap = false)
public class VanillaChunkFadeMixin {
    @ModifyConstant(method = "transform", constant = @Constant(stringValue = "const float mc_chunkFade = -1.0;"))
    private static String vector3$fullyFadedIn(String declaration) {
        return "const float mc_chunkFade = 1.0;";
    }
}
