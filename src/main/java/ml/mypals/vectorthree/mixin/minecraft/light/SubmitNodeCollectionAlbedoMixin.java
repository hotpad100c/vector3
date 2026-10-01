package ml.mypals.vectorthree.mixin.minecraft.light;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.vectorthree.mc.light.AlbedoCapture;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionAlbedoMixin {
    @Inject(method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("HEAD"))
    private void vector3$captureAlbedo(Model<?> model, Object state, PoseStack poseStack, RenderType renderType,
            int light, int overlay, int tint, TextureAtlasSprite sprite, int outline,
            ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        AlbedoCapture.model(model, state, poseStack, renderType, tint, sprite);
    }
}
