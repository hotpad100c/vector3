package ml.mypals.vectorthree.mixin.minecraft.area;

import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.mc.shape.area.AreaShape;
import ml.mypals.vectorthree.mc.shape.model.TexturedObjShape;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
        TexturedObjShape.beginFrame();
        for (String shapeId : ShapeTrackRegistry.shapeIds()) {
            if (ShapeTrackRegistry.shape(shapeId) instanceof AreaShape area) {
                area.submitFrame(collector, levelRenderState.cameraRenderState);
            } else if (ShapeTrackRegistry.shape(shapeId) instanceof TexturedObjShape obj) {
                obj.submitFrame();
            }
        }
    }

    // The main frame pass (addMainPass's executes lambda): opaque after the solid features, translucent just before
    // the translucent terrain, where vanilla draws its own translucent features.
    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid()V"))
    private void vector3$drawAreaOpaque(CallbackInfo ci) {
        AreaShape.drawPreparedOpaque();
        TexturedObjShape.drawPreparedOpaque();
    }

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
    private void vector3$drawAreaTranslucent(CallbackInfo ci) {
        AreaShape.drawPreparedTranslucent();
        TexturedObjShape.drawPreparedTranslucent();
    }
}
