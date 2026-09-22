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
    record Result(MeshData mesh, List<BlockEntity> blockEntities, int blockCount) {}

    private final ModelBlockRenderer modelRenderer;
    private final FluidRenderer fluidRenderer;

    AreaBaker() {
        Minecraft minecraft = Minecraft.getInstance();
        this.modelRenderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());
        this.fluidRenderer = new FluidRenderer(minecraft.getModelManager().getFluidStateModelSet());
    }

    Result bake(ClientLevel level, BlockPos min, BlockPos max) {
        RenderType renderType = AreaRenderType.get();
        BufferBuilder builder = new BufferBuilder(new ByteBufferBuilder(2048),
                renderType.primitiveTopology(), renderType.format());
        Output output = new Output(builder);
        List<BlockEntity> blockEntities = new ArrayList<>();
        int blockCount = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
                        modelRenderer.tesselateBlock(output, x, y, z, level, pos, state, model, pos.asLong());
                        blockCount++;
                    }
                    FluidState fluidState = level.getFluidState(pos);
                    if (!fluidState.isEmpty()) {
                        fluidRenderer.tesselate(level, pos, output, state, fluidState);
                    }
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) blockEntities.add(blockEntity);
                }
            }
        }
        return new Result(builder.build(), blockEntities, blockCount);
    }

    private record Output(BufferBuilder builder) implements BlockQuadOutput, FluidRenderer.Output {
        @Override
        public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        }

        @Override
        public VertexConsumer getBuilder(ChunkSectionLayer layer) {
            return builder;
        }
    }
}
