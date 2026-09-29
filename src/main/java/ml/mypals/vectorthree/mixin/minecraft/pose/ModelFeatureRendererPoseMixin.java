package ml.mypals.vectorthree.mixin.minecraft.pose;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ml.mypals.vectorthree.mc.pose.EntityPoses;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Every submitted model is set up again here before it is drawn: the entity model and its armour, cape, elytra.
@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererPoseMixin {
    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private void vector3$poseModel(Model<?> model, Object state, Operation<Void> original) {
        original.call(model, state);
        EntityPoses.afterSetupAnim(model, state, false);
    }
}
