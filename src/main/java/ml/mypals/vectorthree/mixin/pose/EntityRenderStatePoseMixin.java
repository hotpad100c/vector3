package ml.mypals.vectorthree.mixin.pose;

import ml.mypals.vectorthree.flashback.pose.EntityPose;
import ml.mypals.vectorthree.flashback.pose.EntityPoses;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.UUID;

@Mixin(EntityRenderState.class)
public class EntityRenderStatePoseMixin implements EntityPoses.Holder {
    @Unique private UUID vector3$poseEntity;
    @Unique private List<EntityPose> vector3$poses = List.of();

    @Override public UUID vector3$poseEntity() { return vector3$poseEntity; }
    @Override public List<EntityPose> vector3$poses() { return vector3$poses; }

    @Override
    public void vector3$setPoses(UUID entity, List<EntityPose> poses) {
        vector3$poseEntity = entity;
        vector3$poses = poses;
    }
}
