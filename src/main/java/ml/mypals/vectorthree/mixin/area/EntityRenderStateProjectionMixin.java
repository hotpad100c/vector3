package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.area.AreaProjection;
import ml.mypals.vectorthree.shape.area.ProjectedEntityState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateProjectionMixin implements ProjectedEntityState {
    @Unique private AreaProjection.Projection vector3$projection;

    @Override public AreaProjection.Projection vector3$projection() { return vector3$projection; }
    @Override public void vector3$setProjection(AreaProjection.Projection projection) { vector3$projection = projection; }
}
