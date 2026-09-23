package ml.mypals.vectorthree.mixin.area;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import ml.mypals.vectorthree.shape.AreaBlockEntityTranslucency;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * RenderType#prepare writes each batch's transform with a hardcoded white color modulator. For an
 * AreaShape's translucent block entity variants, write the shape's color instead, so the alpha applies
 * without rebuilding any vertex data.
 */
@Mixin(RenderType.class)
public class AreaBlockEntityAlphaMixin {
    @WrapOperation(method = "writeDynamicTransforms", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private GpuBufferSlice vector3$applyAreaAlpha(DynamicGpuData data, Matrix4f modelView, Matrix4f textureMatrix,
            Operation<GpuBufferSlice> original) {
        Vector4f modulator = AreaBlockEntityTranslucency.modulatorFor((RenderType) (Object) this);
        return modulator == null
                ? original.call(data, modelView, textureMatrix)
                : data.writeTransform(modelView, modulator, new Vector3f(), textureMatrix);
    }
}
