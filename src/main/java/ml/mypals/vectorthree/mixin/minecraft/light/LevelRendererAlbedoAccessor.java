package ml.mypals.vectorthree.mixin.minecraft.light;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAlbedoAccessor {
    @Accessor("featureRenderDispatcher")
    FeatureRenderDispatcher vector3$featureRenderDispatcher();
}
