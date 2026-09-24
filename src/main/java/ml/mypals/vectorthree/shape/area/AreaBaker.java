package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;


final class AreaBaker {
    /** Split so AreaShape can draw solid/cutout unsorted and re-sort the translucent bucket per frame.
     *  outlineVertices is CPU-side (position+uv only, see OutlineVertex) rather than a GPU mesh — the
     *  outline has to be resubmitted through LevelRenderer's own SubmitNodeStorage every frame it's
     *  visible (see AreaOutlineSubmitMixin), which only accepts immediate per-vertex geometry. */
    record Result(MeshData solidMesh, MeshData cutoutMesh, MeshData translucentMesh,
            List<OutlineVertex> outlineVertices, List<BlockEntity> blockEntities, int blockCount) {}

    record OutlineVertex(float x, float y, float z, float u, float v) {}

    private final ModelBlockRenderer modelRenderer;
    private final FluidRenderer fluidRenderer;

    AreaBaker() {
        Minecraft minecraft = Minecraft.getInstance();
        this.modelRenderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());
        this.fluidRenderer = new FluidRenderer(minecraft.getModelManager().getFluidStateModelSet());
    }

    Result bake(ClientLevel level, BlockPos min, BlockPos max) {
        BufferBuilder solidBuilder = newBuilder(AreaRenderType.getSolid());
        BufferBuilder cutoutBuilder = newBuilder(AreaRenderType.getCutout());
        BufferBuilder translucentBuilder = newBuilder(AreaRenderType.get());
        Output output = new Output(solidBuilder, cutoutBuilder, translucentBuilder);
        // Every quad routes to the same single target regardless of layer — the outline is one
        // unified silhouette of the whole bake, not split by material. Vertex color is ignored (see
        // OutlineCapture) since the outline is always filled with the shape's own outlineColor.
        OutlineCapture outlineCapture = new OutlineCapture();
        Output outlineOutput = new Output(outlineCapture, outlineCapture, outlineCapture);
        List<BlockEntity> blockEntities = new ArrayList<>();
        int[] blockCount = {0};

        // Reads outside the AABB as air/empty, so a block at the selection boundary doesn't cull its
        // outward-facing side against a real neighbor that won't exist at the destination.
        AreaBakeView view = new AreaBakeView(level, min, max);

        // AreaSuppression already covers this exact AABB by the time a re-bake runs (only the very
        // first bake predates AreaSuppression.set()) — without bypassing, tesselateBlock/tesselate
        // would suppress their own source content right back out of the mesh they're building.
        AreaSuppression.bypassing(() -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        pos.set(x, y, z);
                        BlockState state = view.getBlockState(pos);
                        if (!state.isAir()) {
                            var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
                            modelRenderer.tesselateBlock(output, x, y, z, view, pos, state, model, pos.asLong());
                            modelRenderer.tesselateBlock(outlineOutput, x, y, z, view, pos, state, model, pos.asLong());
                            blockCount[0]++;
                        }
                        FluidState fluidState = view.getFluidState(pos);
                        if (!fluidState.isEmpty()) {
                            output.currentFluidPos = pos;
                            fluidRenderer.tesselate(view, pos, output, state, fluidState);
                            outlineOutput.currentFluidPos = pos;
                            fluidRenderer.tesselate(view, pos, outlineOutput, state, fluidState);
                        }
                        BlockEntity blockEntity = view.getBlockEntity(pos);
                        if (blockEntity != null) blockEntities.add(blockEntity);
                    }
                }
            }
        });
        return new Result(solidBuilder.build(), cutoutBuilder.build(), translucentBuilder.build(),
                outlineCapture.finish(), blockEntities, blockCount[0]);
    }

    private static BufferBuilder newBuilder(RenderType renderType) {
        return new BufferBuilder(new ByteBufferBuilder(2048), renderType.primitiveTopology(), renderType.format());
    }

    /**
     * Bridges ModelBlockRenderer/FluidRenderer's per-block output callbacks into up to three
     * VertexConsumer targets, routing each quad by its own {@code ChunkSectionLayer} (blocks: from the
     * BakedQuad's own material info; fluids: from the layer FluidRenderer itself asks for via
     * getBuilder). Works against any VertexConsumer — a GPU-bound BufferBuilder for solid/cutout/
     * translucent, or an OutlineCapture for the CPU-side outline.
     * <p>
     * FluidRenderer.tesselate writes vertex positions as {@code (pos.getX() & 15, ...)} — section-local
     * 0..15, not the absolute x/y/z we pass ModelBlockRenderer.tesselateBlock/putBlockBakedQuad — since
     * real chunk rendering re-adds the section origin via a per-section transform at draw time, which we
     * don't have. getBuilder() wraps the chosen target to add that origin back itself, so fluid vertices
     * land in the same absolute-world space as block vertices.
     */
    private static final class Output implements BlockQuadOutput, FluidRenderer.Output {
        private final VertexConsumer solidTarget;
        private final VertexConsumer cutoutTarget;
        private final VertexConsumer translucentTarget;
        private BlockPos currentFluidPos;

        Output(VertexConsumer solidTarget, VertexConsumer cutoutTarget, VertexConsumer translucentTarget) {
            this.solidTarget = solidTarget;
            this.cutoutTarget = cutoutTarget;
            this.translucentTarget = translucentTarget;
        }

        private VertexConsumer targetFor(ChunkSectionLayer layer) {
            if (layer == ChunkSectionLayer.TRANSLUCENT) return translucentTarget;
            if (layer == ChunkSectionLayer.CUTOUT) return cutoutTarget;
            return solidTarget;
        }

        @Override
        public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            targetFor(quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
        }

        @Override
        public VertexConsumer getBuilder(ChunkSectionLayer layer) {
            float dx = currentFluidPos.getX() - (currentFluidPos.getX() & 15);
            float dy = currentFluidPos.getY() - (currentFluidPos.getY() & 15);
            float dz = currentFluidPos.getZ() - (currentFluidPos.getZ() & 15);
            return new SectionOffsetVertexConsumer(targetFor(layer), dx, dy, dz);
        }
    }

    /** Adds a constant offset to every vertex position written through it; everything else passes through. */
    private record SectionOffsetVertexConsumer(VertexConsumer delegate, float dx, float dy, float dz)
            implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x + dx, y + dy, z + dz);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            delegate.setColor(argb);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            delegate.setUv3(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
    }

    /**
     * Records position+uv (only what OutlineVertex needs — the outline is always filled with a flat
     * outlineColor at submit time, not the baked per-vertex AO color) instead of writing into a GPU
     * buffer, since the outline has to be replayed through SubmitNodeStorage.submitCustomGeometry's
     * immediate-mode callback each frame. addVertex starts a new vertex; its attributes arrive via the
     * following setXxx calls, so each vertex is only committed once the next one starts (or finish()
     * runs) — the same "pending vertex" pattern BufferBuilder itself uses internally.
     */
    private static final class OutlineCapture implements VertexConsumer {
        private final List<OutlineVertex> vertices = new ArrayList<>();
        private boolean hasPending;
        private float px, py, pz, pu, pv;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flushPending();
            hasPending = true;
            px = x; py = y; pz = z; pu = 0; pv = 0;
            return this;
        }

        private void flushPending() {
            if (hasPending) vertices.add(new OutlineVertex(px, py, pz, pu, pv));
            hasPending = false;
        }

        List<OutlineVertex> finish() {
            flushPending();
            return vertices;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            pu = u; pv = v;
            return this;
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setColor(int argb) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setUv3(float u, float v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
