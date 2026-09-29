package ml.mypals.vectorthree.mixin.minecraft.iris.skyOverride;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import ml.mypals.vectorthree.core.port.Ports;

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

        var sky = Ports.view().skyOverride();
        if (sky != null) fogColor.set(sky.r(), sky.g(), sky.b(), sky.a());
        original.call(resourceAllocator, renderOutline, cameraState, terrainFog, fogColor, shouldRenderSky, consistentDepthRequired);

    }

}
