package ml.mypals.vectorthree.mc.shape.area;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;


final class AreaGpuMesh {
    private static final int VERTEX_USAGE = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST;

    private GpuBuffer vertexBuffer;
    private int indexCount;
    private PrimitiveTopology topology = PrimitiveTopology.TRIANGLES;

    void upload(MeshData mesh) {
        close();
        if (mesh == null) return;
        try {
            MeshData.DrawState drawState = mesh.drawState();
            this.indexCount = drawState.indexCount();
            this.topology = drawState.primitiveTopology();
            this.vertexBuffer = RenderSystem.getDevice()
                    .createBuffer(() -> "vector3_area/vertex", VERTEX_USAGE, mesh.vertexBuffer());
        } finally {
            mesh.close();
        }
    }

    /** Like upload, but writes into the current buffer when it is big enough, so a mesh rebuilt every frame
     *  doesn't allocate a new one each time. */
    void uploadReusing(MeshData mesh) {
        if (mesh == null) {
            indexCount = 0;
            return;
        }
        try {
            MeshData.DrawState drawState = mesh.drawState();
            java.nio.ByteBuffer bytes = mesh.vertexBuffer();
            int size = bytes.remaining();
            if (vertexBuffer == null || vertexBuffer.size() < size) {
                close();
                vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "vector3_area/vertex", VERTEX_USAGE,
                        Math.max(size + size / 2, 1 << 16));
            }
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(vertexBuffer.slice(0, size), bytes);
            indexCount = drawState.indexCount();
            topology = drawState.primitiveTopology();
        } finally {
            mesh.close();
        }
    }

    boolean isEmpty() {
        return vertexBuffer == null || indexCount == 0;
    }

    GpuBuffer vertexBuffer() { return vertexBuffer; }
    int indexCount() { return indexCount; }
    PrimitiveTopology topology() { return topology; }

    void close() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        indexCount = 0;
    }
}
