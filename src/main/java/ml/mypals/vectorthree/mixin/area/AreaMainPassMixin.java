package ml.mypals.vectorthree.mixin.area;

import com.mojang.renderpearl.api.commands.RenderPass;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.shape.area.AreaShape;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class AreaMainPassMixin {
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void vector3$submitAreas(LevelRenderState levelRenderState, SubmitNodeCollector collector, boolean bl,
            CallbackInfo ci) {
        ShapeTrackRegistry.updateMounts();
        AreaShape.beginFrame();
        for (String shapeId : ShapeTrackRegistry.shapeIds()) {
            if (ShapeTrackRegistry.shape(shapeId) instanceof AreaShape area) {
                area.submitFrame(collector, levelRenderState.cameraRenderState);
            }
        }
    }

    @Inject(method = "executeSolid", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void vector3$drawAreaOpaque(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame frame,
            RenderPass pass, CallbackInfo ci) {
        AreaShape.drawPreparedOpaque(pass);
    }

    @Inject(method = "executeClassicTransparency", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
    private void vector3$drawAreaTranslucent(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame frame,
            RenderPass pass, CallbackInfo ci) {
        AreaShape.drawPreparedTranslucent(pass);
    }

    @Inject(method = "executeOit", at = @At("HEAD"))
    private void vector3$drawAreaTranslucentOit(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame frame,
            CallbackInfo ci) {
        AreaShape.drawPreparedTranslucent();
    }
}
