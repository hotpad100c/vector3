package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

final class AreaTranslucentGpuMesh {
    private static final int VERTEX_USAGE = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST;
    private static final int INDEX_USAGE = GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST;

    private GpuBuffer vertexBuffer;
    private GpuBuffer indexBuffer;
    private MeshData.SortState sortState;
    private ByteBuffer scratchIndices;
    private IndexType indexType = IndexType.SHORT;
    private PrimitiveTopology topology = PrimitiveTopology.TRIANGLES;
    private int indexCount;

    void upload(MeshData mesh) {
        close();
        if (mesh == null) return;
        try {
            MeshData.DrawState drawState = mesh.drawState();
            indexCount = drawState.indexCount();
            topology = drawState.primitiveTopology();
            indexType = drawState.indexType();
            if (indexCount == 0 || topology != PrimitiveTopology.QUADS) return;

            var device = RenderSystem.getDevice();
            vertexBuffer = device.createBuffer(() -> "vector3_area/translucent_vertex", VERTEX_USAGE, mesh.vertexBuffer());
            // scratchBuilder backs mesh.indexBuffer()'s Result — must stay open until that's been read.
            try (ByteBufferBuilder scratchBuilder = new ByteBufferBuilder(indexCount * indexType.bytes)) {
                sortState = mesh.sortQuads(scratchBuilder, VertexSorting.DISTANCE_TO_ORIGIN);
                if (sortState == null) return;
                indexBuffer = device.createBuffer(() -> "vector3_area/translucent_index", INDEX_USAGE, mesh.indexBuffer());
            }
            scratchIndices = MemoryUtil.memAlloc(indexCount * indexType.bytes);
        } finally {
            mesh.close();
        }
    }

    boolean isEmpty() {
        return vertexBuffer == null || indexBuffer == null || sortState == null || indexCount == 0;
    }

    void resort(Vector3fc meshSpaceViewPoint) {
        scratchIndices.clear();
        sortState.writeSortedIndexBuffer(scratchIndices, VertexSorting.byDistance(meshSpaceViewPoint));
        scratchIndices.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(indexBuffer.slice(), scratchIndices);
    }

    GpuBuffer vertexBuffer() { return vertexBuffer; }
    GpuBuffer indexBuffer() { return indexBuffer; }
    IndexType indexType() { return indexType; }
    int indexCount() { return indexCount; }
    PrimitiveTopology topology() { return topology; }

    void close() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (indexBuffer != null) {
            indexBuffer.close();
            indexBuffer = null;
        }
        if (scratchIndices != null) {
            MemoryUtil.memFree(scratchIndices);
            scratchIndices = null;
        }
        sortState = null;
        indexCount = 0;
    }
}
