package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.impl.TrackEntityKeyframe;
import ml.mypals.vectorthree.flashback.pose.ModelPartCombo;
import ml.mypals.vectorthree.flashback.pose.ModelPartHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

// Track Entity keyframes can anchor the camera on one part of the target's model; see TrackEntityChangeModelPartMixin.
@Mixin(value = TrackEntityKeyframe.class, remap = false)
public class TrackEntityModelPartMixin implements ModelPartHolder {
    @Unique private String vector3$modelPart;

    @Override public String vector3$modelPart() { return vector3$modelPart; }
    @Override public void vector3$setModelPart(String part) { vector3$modelPart = part; }

    @Inject(method = {"copy", "createChange", "createSmoothInterpolatedChange", "createHermiteInterpolatedChange"},
            at = @At("RETURN"))
    private void vector3$carryModelPart(CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() instanceof ModelPartHolder holder && holder.vector3$modelPart() == null) {
            holder.vector3$setModelPart(vector3$modelPart);
        }
    }

    @Inject(method = "renderEditKeyframe", at = @At("TAIL"))
    private void vector3$modelPartCombo(Consumer<Consumer<Keyframe>> update, CallbackInfo ci) {
        UUID target = ((TrackEntityKeyframe) (Object) this).target;
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = target == null || minecraft.level == null ? null : minecraft.level.getEntity(target);
        String picked = ModelPartCombo.render(I18n.get("vector3.mount.model_part"), entity, vector3$modelPart);
        if (!Objects.equals(picked, vector3$modelPart)) {
            update.accept(keyframe -> ((ModelPartHolder) keyframe).vector3$setModelPart(picked));
        }
    }
}
