package ml.mypals.vectorthree.shape.area;

import org.jetbrains.annotations.Nullable;
public interface ProjectedEntityState {
    @Nullable AreaProjection.Projection vector3$projection();

    void vector3$setProjection(@Nullable AreaProjection.Projection projection);
}
