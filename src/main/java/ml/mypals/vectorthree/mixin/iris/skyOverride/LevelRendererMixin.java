package ml.mypals.vectorthree.mixin.iris.skyOverride;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;

@Debug(export = true)
@Mixin(value = LevelRenderer.class, priority = 900)
public class LevelRendererMixin {

    @WrapMethod(
            method = {"render"}
    )
    public void renderLevel(GraphicsResourceAllocator resourceAllocator, boolean renderOutline, CameraRenderState cameraState, com.mojang.renderpearl.api.buffers.GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, boolean consistentDepthRequired, Operation<Void> original) {

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;
            if (!visuals.renderSky) {
                if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
                    fogColor.set(0.0F, 0.0F, 0.0F, 0.0F);

                } else {
                    float[] skyColour = visuals.skyColour;
                    fogColor.set(skyColour[0], skyColour[1], skyColour[2], 1.0F);
                }
            }
        }
        original.call(resourceAllocator, renderOutline, cameraState, terrainFog, fogColor, shouldRenderSky, consistentDepthRequired);

    }

}
