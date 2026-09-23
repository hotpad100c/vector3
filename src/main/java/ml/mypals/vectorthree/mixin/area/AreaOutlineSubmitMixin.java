package ml.mypals.vectorthree.mixin.area;

import ml.mypals.vectorthree.shape.AreaShape;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class AreaOutlineSubmitMixin {
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void vector3$submitAreaOutlines(LevelRenderState levelRenderState, SubmitNodeCollector collector,
            boolean bl, CallbackInfo ci) {
        if (!(collector instanceof SubmitNodeStorage submits) || levelRenderState.cameraRenderState == null) return;
        Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
        boolean any = false;
        for (String shapeId : ShapeTrackRegistry.shapeIds()) {
            if (ShapeTrackRegistry.shape(shapeId) instanceof AreaShape area && area.hasOutline()) {
                area.submitOutline(submits, cameraPos);
                any = true;
            }
        }
        if (any) levelRenderState.shouldShowEntityOutlines = true;
    }
}
