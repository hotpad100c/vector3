package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTrackEntity;
import ml.mypals.vectorthree.flashback.pose.EntityParts;
import ml.mypals.vectorthree.flashback.pose.ModelPartHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyframeChangeTrackEntity.class, remap = false)
public class TrackEntityChangeModelPartMixin implements ModelPartHolder {
    @Unique private String vector3$modelPart;

    @Override public String vector3$modelPart() { return vector3$modelPart; }
    @Override public void vector3$setModelPart(String part) { vector3$modelPart = part; }

    // Not blended: the part switches halfway, like the tracked entity.
    @Inject(method = "interpolate", at = @At("RETURN"))
    private void vector3$interpolateModelPart(KeyframeChange other, double amount, CallbackInfoReturnable<KeyframeChange> cir) {
        if (!(cir.getReturnValue() instanceof ModelPartHolder result)) return;
        String otherPart = other instanceof ModelPartHolder holder ? holder.vector3$modelPart() : null;
        result.vector3$setModelPart(amount < 0.5 ? vector3$modelPart : otherPart);
    }

    // The anchor Flashback computes from the body part (eyes / middle / feet) becomes the model part's pivot; the
    // offsets and view rotation then apply to it as usual.
    @ModifyVariable(method = "apply", at = @At("STORE"), ordinal = 0)
    private Vec3 vector3$anchorOnModelPart(Vec3 anchor, @Local(ordinal = 0) Entity entity, @Local(ordinal = 0) float partialTick) {
        if (vector3$modelPart == null || !(entity instanceof LivingEntity living)) return anchor;
        Matrix4f part = EntityParts.transform(living, vector3$modelPart, partialTick);
        if (part == null) return anchor;
        Vector3f pivot = part.getTranslation(new Vector3f());
        return new Vec3(pivot.x, pivot.y, pivot.z);
    }
}
