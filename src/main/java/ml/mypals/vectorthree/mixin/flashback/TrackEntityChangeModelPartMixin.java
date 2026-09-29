package ml.mypals.vectorthree.mixin.flashback;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTrackEntity;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import ml.mypals.vectorthree.mc.pose.EntityParts;
import ml.mypals.vectorthree.core.pose.ModelPartHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyframeChangeTrackEntity.class, remap = false)
public class TrackEntityChangeModelPartMixin implements ModelPartHolder {
    @Unique private String vector3$modelPart;
    @Unique private boolean vector3$followPartRotation;

    @Override public String vector3$modelPart() { return vector3$modelPart; }
    @Override public void vector3$setModelPart(String part) { vector3$modelPart = part; }
    @Override public boolean vector3$followPartRotation() { return vector3$followPartRotation; }
    @Override public void vector3$setFollowPartRotation(boolean follow) { vector3$followPartRotation = follow; }

    // Not blended: the part switches halfway, like the tracked entity.
    @Inject(method = "interpolate", at = @At("RETURN"))
    private void vector3$interpolateModelPart(KeyframeChange other, double amount, CallbackInfoReturnable<KeyframeChange> cir) {
        if (!(cir.getReturnValue() instanceof ModelPartHolder result)) return;
        ModelPartHolder.copy(amount < 0.5 || !(other instanceof ModelPartHolder) ? this : other, result);
    }

    /*
     * Following the part's rotation: the camera is the part's orientation turned by the yaw / pitch / roll offsets.
     * Its frame and the camera's share axes (right +X, up +Y, forward -Z), and the camera Flashback applies is
     * rotationYXZ(180 - yaw, -pitch, 0) turned by roll about Z, so an unrotated part gives Flashback's own result.
     */
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void vector3$followModelPart(KeyframeHandler handler, CallbackInfo ci) {
        if (vector3$modelPart == null || !vector3$followPartRotation) return;
        Minecraft minecraft = handler.getMinecraft();
        if (minecraft == null || minecraft.level == null) return;
        KeyframeChangeTrackEntity self = (KeyframeChangeTrackEntity) (Object) this;
        Entity entity = minecraft.level.getEntity(self.target());
        if (entity == null) return;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Matrix4f part = EntityParts.transform(entity, vector3$modelPart, partialTick);
        if (part == null) return;

        Quaternionf facing = part.getNormalizedRotation(new Quaternionf())
                .rotateY((float) Math.toRadians(-self.yawOffset()))
                .rotateX((float) Math.toRadians(-self.pitchOffset()));
        Vector3f angles = new Quaternionf(facing).rotateZ((float) Math.toRadians(self.roll()))
                .getEulerAnglesYXZ(new Vector3f());
        // As in Flashback, the view offset turns with the camera (but not its roll) and the position offset doesn't.
        Vector3f view = facing.transform(new Vector3f((float) self.viewOffset().x, (float) self.viewOffset().y,
                (float) self.viewOffset().z));
        Vector3f pivot = part.getTranslation(new Vector3f());
        Vector3d position = new Vector3d(pivot.x + self.positionOffset().x + view.x,
                pivot.y + self.positionOffset().y + view.y, pivot.z + self.positionOffset().z + view.z);
        if (minecraft.player != null) position.y -= minecraft.player.getEyeHeight();
        handler.applyCameraPosition(position, 180 - Math.toDegrees(angles.y), -Math.toDegrees(angles.x),
                Math.toDegrees(angles.z));
        ci.cancel();
    }

    // Flashback samples the entity with the frozen game's running sub-tick, but a paused replay renders entities at
    // 1.0, so the camera shook against a still entity. Same partial tick as the world render.
    @ModifyVariable(method = "apply", at = @At("STORE"), ordinal = 0)
    private float vector3$worldPartialTick(float partialTick) {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    // The anchor Flashback computes from the body part (eyes / middle / feet) becomes the model part's pivot; the
    // offsets and view rotation then apply to it as usual.
    @ModifyVariable(method = "apply", at = @At("STORE"), ordinal = 0)
    private Vec3 vector3$anchorOnModelPart(Vec3 anchor, @Local(ordinal = 0) Entity entity, @Local(ordinal = 0) float partialTick) {
        if (vector3$modelPart == null || entity == null) return anchor;
        Matrix4f part = EntityParts.transform(entity, vector3$modelPart, partialTick);
        if (part == null) return anchor;
        Vector3f pivot = part.getTranslation(new Vector3f());
        return new Vec3(pivot.x, pivot.y, pivot.z);
    }
}
