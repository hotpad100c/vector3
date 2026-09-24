package ml.mypals.vectorthree.shape.area;

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
