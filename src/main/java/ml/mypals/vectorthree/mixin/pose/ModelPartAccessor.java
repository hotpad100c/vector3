package ml.mypals.vectorthree.mixin.pose;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children") Map<String, ModelPart> vector3$children();
}
