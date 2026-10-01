package ml.mypals.vectorthree.mc.render;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a {@link PreparedRenderType} into a pass the caller already holds. 26.2's own
 * {@link PreparedRenderType#drawFromBuffer} opens a render pass per draw.
 */
public final class PreparedDraws {
    private PreparedDraws() {}

    /** {@code type} with its DynamicTransforms swapped for {@code transform}. */
    public static PreparedRenderType withTransform(PreparedRenderType type, GpuBufferSlice transform) {
        return new PreparedRenderType(type.pipeline(), type.outputTarget(), transform, type.scissorState(), type.textures());
    }

    /**
     * Draws {@code indexCount} indices from {@code firstIndex} of {@code vertices}. A null {@code indices} draws with
     * the shared sequential index buffer for {@code topology}, grown to fit.
     */
    public static void draw(RenderPass pass, PreparedRenderType type, GpuBuffer vertices, @Nullable GpuBuffer indices,
                            @Nullable IndexType indexType, int baseVertex, int firstIndex, int indexCount,
                            PrimitiveTopology topology) {
        if (indices == null) {
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(topology);
            indices = sequential.getBuffer(firstIndex + indexCount);
            indexType = sequential.type();
        }
        pass.setPipeline(type.pipeline());
        if (type.scissorState().enabled()) {
            pass.enableScissor(type.scissorState().x(), type.scissorState().y(), type.scissorState().width(),
                    type.scissorState().height());
        }
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", type.dynamicTransforms());
        pass.setVertexBuffer(0, vertices.slice());
        for (PreparedRenderType.Texture texture : type.textures()) {
            pass.bindTexture(texture.name(), texture.textureView(), texture.sampler());
        }
        pass.setIndexBuffer(indices, indexType);
        pass.drawIndexed(indexCount, 1, firstIndex, baseVertex, 0);
        if (type.scissorState().enabled()) pass.disableScissor();
    }

    /** Grows the shared sequential index buffer for {@code topology} before a pass that draws {@code indexCount} from it. */
    public static void reserveSequential(PrimitiveTopology topology, int indexCount) {
        RenderSystem.getSequentialBuffer(topology).getBuffer(indexCount);
    }
}
