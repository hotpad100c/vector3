package ml.mypals.vectorthree.mixin.pose;

import ml.mypals.vectorthree.flashback.pose.EntityPoses;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EnderDragonRenderer.class)
public class EnderDragonRendererPoseMixin implements EntityPoses.ModelOwner {
    @Shadow @Final private EnderDragonModel model;

    @Override
    public Model<?> vector3$poseModel() {
        return model;
    }
}
