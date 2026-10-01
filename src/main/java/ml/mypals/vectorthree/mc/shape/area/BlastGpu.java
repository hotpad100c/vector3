package ml.mypals.vectorthree.mc.shape.area;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.buffers.GpuBuffer;
import ml.mypals.vectorthree.core.shape.blast.BlastMotion;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * A blast's geometry, uploaded once: every layer's quads in clump order (so a clump at any level is one run), with
 * positions relative to the region's center. Per level, an index buffer leaves out the faces hidden between blocks
 * of the same clump; {@code starts[level][layer][i]} is where the i-th block (in clump order) begins in it. A texel
 * buffer maps each vertex to its block for the GPU-skinned draw.
 */
final class BlastGpu implements AutoCloseable {
    static final int LAYERS = 3;

    final GpuBuffer[] vertices = new GpuBuffer[LAYERS];
    final GpuBuffer[] blockOf = new GpuBuffer[LAYERS];
    final GpuBuffer[][] indices = new GpuBuffer[BlastMotion.MAX_LEVEL + 1][LAYERS];
    final int[][][] starts = new int[BlastMotion.MAX_LEVEL + 1][LAYERS][];
    final boolean extended;

    BlastGpu(List<BlastBaker.Piece> pieces, BlastMotion motion, int[] order, Vec3 center, boolean extended) {
        this.extended = extended;
        int count = order.length;
        for (int layer = 0; layer < LAYERS; layer++) {
            RenderType type = layer == 0 ? AreaRenderType.getSolid() : layer == 1 ? AreaRenderType.getCutout() : AreaRenderType.get();
            int[] firstQuad = new int[count];
            int quads = 0;
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 16)) {
                BufferBuilder builder = new BufferBuilder(bytes, type.primitiveTopology(), type.format());
                for (int position = 0; position < count; position++) {
                    BlastBaker.Piece piece = pieces.get(order[position]);
                    BlastBaker.Layer data = piece.layer(layer);
                    firstQuad[position] = quads;
                    quads += data.quadCount();
                    float ox = (float) (piece.pos().getX() - center.x), oy = (float) (piece.pos().getY() - center.y),
                            oz = (float) (piece.pos().getZ() - center.z);
                    float[] values = data.data.elements();
                    int[] colors = data.colors.elements(), lights = data.lights.elements();
                    for (int v = 0; v < data.vertexCount(); v++) {
                        int at = v * 8;
                        builder.addVertex(values[at] + ox, values[at + 1] + oy, values[at + 2] + oz, colors[v],
                                values[at + 3], values[at + 4], OverlayTexture.NO_OVERLAY, lights[v],
                                values[at + 5], values[at + 6], values[at + 7]);
                    }
                }
                try (MeshData mesh = builder.build()) {
                    if (mesh != null) {
                        String label = "vector3_blast/vertex_" + layer;
                        vertices[layer] = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                    }
                }
            }
            if (vertices[layer] == null) quads = 0;
            blockOf[layer] = quads == 0 ? null : blockOfBuffer(pieces, order, layer, quads * 4);
            for (int level = 0; level <= BlastMotion.MAX_LEVEL; level++) {
                int[] start = new int[count + 1];
                ByteBuffer indexBytes = MemoryUtil.memAlloc(Math.max(4, quads * 6 * 4));
                try {
                    int written = 0;
                    for (int position = 0; quads > 0 && position < count; position++) {
                        start[position] = written;
                        int block = order[position];
                        BlastBaker.Layer data = pieces.get(block).layer(layer);
                        int[] occluders = data.occluders.elements();
                        for (int quad = 0; quad < data.quadCount(); quad++) {
                            int occluder = occluders[quad];
                            if (occluder >= 0 && motion.sharedLevel(block, occluder) <= level) continue;
                            int base = (firstQuad[position] + quad) * 4;
                            indexBytes.putInt(base).putInt(base + 1).putInt(base + 2)
                                    .putInt(base + 2).putInt(base + 3).putInt(base);
                            written += 6;
                        }
                    }
                    start[count] = written;
                    if (quads == 0) java.util.Arrays.fill(start, 0);
                    starts[level][layer] = start;
                    if (written > 0) {
                        indexBytes.flip();
                        String label = "vector3_blast/index_" + level + "_" + layer;
                        indices[level][layer] = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_INDEX, indexBytes);
                    }
                } finally {
                    MemoryUtil.memFree(indexBytes);
                }
            }
        }
    }

    private static GpuBuffer blockOfBuffer(List<BlastBaker.Piece> pieces, int[] order, int layer, int vertexCount) {
        ByteBuffer bytes = MemoryUtil.memAlloc(vertexCount * 4);
        try {
            for (int block : order) {
                int vertices = pieces.get(block).layer(layer).vertexCount();
                for (int v = 0; v < vertices; v++) bytes.putInt(block);
            }
            bytes.flip();
            return RenderSystem.getDevice().createBuffer(() -> "vector3_blast/block_of_" + layer,
                    GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    boolean isEmpty() {
        for (GpuBuffer buffer : vertices) if (buffer != null) return false;
        return true;
    }

    @Override
    public void close() {
        for (int layer = 0; layer < LAYERS; layer++) {
            if (vertices[layer] != null) vertices[layer].close();
            if (blockOf[layer] != null) blockOf[layer].close();
            for (GpuBuffer[] level : indices) if (level[layer] != null) level[layer].close();
        }
    }
}
