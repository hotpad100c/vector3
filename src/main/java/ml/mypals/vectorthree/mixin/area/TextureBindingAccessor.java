package ml.mypals.vectorthree.mixin.area;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface TextureBindingAccessor {
    @Accessor("location")
    Identifier vector3$location();
}
