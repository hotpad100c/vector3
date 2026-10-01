package ml.mypals.vectorthree.mixin.minecraft.iris.skyOverride;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import ml.mypals.vectorthree.core.port.Ports;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;

@Debug(export = true)
@Mixin(value = LevelRenderer.class, priority = 900)
public class LevelRendererMixin {

    @WrapMethod(
            method = {"render"}
    )
    public void renderLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline,
            CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor,
            boolean shouldRenderSky, Operation<Void> original) {

        var sky = Ports.view().skyOverride();
        if (sky != null) fogColor.set(sky.r(), sky.g(), sky.b(), sky.a());
        original.call(resourceAllocator, deltaTracker, renderOutline, cameraState, modelViewMatrix, terrainFog, fogColor, shouldRenderSky);

    }

}
