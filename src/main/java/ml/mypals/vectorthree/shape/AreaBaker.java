package ml.mypals.vectorthree.shape;

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
    /** Split so AreaShape can draw solid/cutout unsorted and re-sort the translucent bucket per frame. */
    record Result(MeshData solidMesh, MeshData cutoutMesh, MeshData translucentMesh,
            List<BlockEntity> blockEntities, int blockCount) {}

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
                            blockCount[0]++;
                        }
                        FluidState fluidState = view.getFluidState(pos);
                        if (!fluidState.isEmpty()) {
                            output.currentFluidPos = pos;
                            fluidRenderer.tesselate(view, pos, output, state, fluidState);
                        }
                        BlockEntity blockEntity = view.getBlockEntity(pos);
                        if (blockEntity != null) blockEntities.add(blockEntity);
                    }
                }
            }
        });
        return new Result(solidBuilder.build(), cutoutBuilder.build(), translucentBuilder.build(),
                blockEntities, blockCount[0]);
    }

    private static BufferBuilder newBuilder(RenderType renderType) {
        return new BufferBuilder(new ByteBufferBuilder(2048), renderType.primitiveTopology(), renderType.format());
    }

    /**
     * Bridges ModelBlockRenderer/FluidRenderer's per-block output callbacks into the solid/cutout/
     * translucent buffer triple, routing each quad by its own {@code ChunkSectionLayer} (blocks: from
     * the BakedQuad's own material info; fluids: from the layer FluidRenderer itself asks for via
     * getBuilder).
     * <p>
     * FluidRenderer.tesselate writes vertex positions as {@code (pos.getX() & 15, ...)} — section-local
     * 0..15, not the absolute x/y/z we pass ModelBlockRenderer.tesselateBlock/putBlockBakedQuad — since
     * real chunk rendering re-adds the section origin via a per-section transform at draw time, which we
     * don't have. getBuilder() wraps the chosen builder to add that origin back itself, so fluid vertices
     * land in the same absolute-world space as block vertices.
     */
    private static final class Output implements BlockQuadOutput, FluidRenderer.Output {
        private final BufferBuilder solidBuilder;
        private final BufferBuilder cutoutBuilder;
        private final BufferBuilder translucentBuilder;
        private BlockPos currentFluidPos;

        Output(BufferBuilder solidBuilder, BufferBuilder cutoutBuilder, BufferBuilder translucentBuilder) {
            this.solidBuilder = solidBuilder;
            this.cutoutBuilder = cutoutBuilder;
            this.translucentBuilder = translucentBuilder;
        }

        private BufferBuilder builderFor(ChunkSectionLayer layer) {
            if (layer == ChunkSectionLayer.TRANSLUCENT) return translucentBuilder;
            if (layer == ChunkSectionLayer.CUTOUT) return cutoutBuilder;
            return solidBuilder;
        }

        @Override
        public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            builderFor(quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
        }

        @Override
        public VertexConsumer getBuilder(ChunkSectionLayer layer) {
            float dx = currentFluidPos.getX() - (currentFluidPos.getX() & 15);
            float dy = currentFluidPos.getY() - (currentFluidPos.getY() & 15);
            float dz = currentFluidPos.getZ() - (currentFluidPos.getZ() & 15);
            return new SectionOffsetVertexConsumer(builderFor(layer), dx, dy, dz);
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
}
