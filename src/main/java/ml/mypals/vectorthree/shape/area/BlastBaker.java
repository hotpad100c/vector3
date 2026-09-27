package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Bakes a region block by block for BlastShape: each block keeps its own vertices (block-local, 0..1) so it can
 * move on its own. Every block is meshed as if alone, so no face is missing once they fly apart; the light comes
 * from the region's own light (AreaBakeView).
 */
final class BlastBaker {
    static final int MAX_BLOCKS = 32768;

    /** One layer's vertices of one block: x, y, z, u, v, nx, ny, nz per vertex, plus color and packed light. */
    static final class Layer {
        final FloatArrayList data = new FloatArrayList();
        final IntArrayList colors = new IntArrayList();
        final IntArrayList lights = new IntArrayList();

        int vertexCount() {
            return colors.size();
        }
    }

    record Piece(BlockPos pos, Layer solid, Layer cutout, Layer translucent) {}

    record Result(List<Piece> pieces, boolean truncated) {}

    private final ModelBlockRenderer modelRenderer;
    private final FluidRenderer fluidRenderer;

    BlastBaker() {
        Minecraft minecraft = Minecraft.getInstance();
        this.modelRenderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());
        this.fluidRenderer = new FluidRenderer(minecraft.getModelManager().getFluidStateModelSet());
    }

    Result bake(ClientLevel level, BlockPos min, BlockPos max) {
        List<Piece> pieces = new ArrayList<>();
        boolean[] truncated = {false};
        AreaBakeView region = new AreaBakeView(level, min, max);
        IsolatedView view = new IsolatedView(region);
        AreaSuppression.bypassing(() -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        pos.set(x, y, z);
                        BlockState state = region.getBlockState(pos);
                        FluidState fluid = region.getFluidState(pos);
                        if (state.isAir() && fluid.isEmpty()) continue;
                        if (pieces.size() >= MAX_BLOCKS) {
                            truncated[0] = true;
                            return;
                        }
                        BlockPos at = pos.immutable();
                        view.current = at;
                        Output output = new Output(at);
                        if (!state.isAir()) {
                            var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
                            modelRenderer.tesselateBlock(output, 0, 0, 0, view, at, state, model, at.asLong());
                            output.finish();
                        }
                        if (!fluid.isEmpty()) {
                            fluidRenderer.tesselate(view, at, output, state, fluid);
                            output.finish();
                        }
                        if (output.solid.vertexCount() + output.cutout.vertexCount() + output.translucent.vertexCount() > 0) {
                            pieces.add(new Piece(at, output.solid, output.cutout, output.translucent));
                        }
                    }
                }
            }
        });
        return new Result(pieces, truncated[0]);
    }

    private static final class Output implements BlockQuadOutput, FluidRenderer.Output {
        final Layer solid = new Layer(), cutout = new Layer(), translucent = new Layer();
        private final Recorder[] blocks;
        private final Recorder[] fluids;

        // FluidRenderer writes section-local positions; its recorders make them block-local.
        Output(BlockPos pos) {
            blocks = new Recorder[]{new Recorder(solid, 0, 0, 0), new Recorder(cutout, 0, 0, 0), new Recorder(translucent, 0, 0, 0)};
            float dx = -(pos.getX() & 15), dy = -(pos.getY() & 15), dz = -(pos.getZ() & 15);
            fluids = new Recorder[]{new Recorder(solid, dx, dy, dz), new Recorder(cutout, dx, dy, dz), new Recorder(translucent, dx, dy, dz)};
        }

        private static int index(ChunkSectionLayer layer) {
            return layer == ChunkSectionLayer.TRANSLUCENT ? 2 : layer == ChunkSectionLayer.CUTOUT ? 1 : 0;
        }

        @Override
        public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            blocks[index(quad.materialInfo().layer())].putBlockBakedQuad(x, y, z, quad, instance);
        }

        @Override
        public VertexConsumer getBuilder(ChunkSectionLayer layer) {
            return fluids[index(layer)];
        }

        void finish() {
            for (Recorder recorder : blocks) recorder.finish();
            for (Recorder recorder : fluids) recorder.finish();
        }
    }

    private static final class Recorder implements VertexConsumer {
        private final Layer layer;
        private final float dx, dy, dz;
        private boolean pending;
        private float x, y, z, u, v, nx, ny = 1, nz;
        private int color = -1, light;

        Recorder(Layer layer, float dx, float dy, float dz) {
            this.layer = layer;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        private void flush() {
            if (!pending) return;
            layer.data.add(x);
            layer.data.add(y);
            layer.data.add(z);
            layer.data.add(u);
            layer.data.add(v);
            layer.data.add(nx);
            layer.data.add(ny);
            layer.data.add(nz);
            layer.colors.add(color);
            layer.lights.add(light);
            pending = false;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flush();
            pending = true;
            this.x = x + dx;
            this.y = y + dy;
            this.z = z + dz;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            color = alpha << 24 | red << 16 | green << 8 | blue;
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            color = argb;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            light = u & 0xFFFF | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }

        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv3(float u, float v) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }

        void finish() {
            flush();
        }
    }

    /** Only the block being meshed exists; light comes from the region, open air around blocks that are hidden. */
    private static final class IsolatedView implements BlockAndTintGetter {
        private final AreaBakeView region;
        BlockPos current = BlockPos.ZERO;

        IsolatedView(AreaBakeView region) {
            this.region = region;
        }

        @Override
        public @NonNull BlockState getBlockState(@NonNull BlockPos pos) {
            return pos.equals(current) ? region.getBlockState(pos) : Blocks.AIR.defaultBlockState();
        }

        @Override
        public @NonNull FluidState getFluidState(@NonNull BlockPos pos) {
            return pos.equals(current) ? region.getFluidState(pos) : Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(@NonNull BlockPos pos) {
            return null;
        }

        // A face against a neighbor inside the region is hidden until the blocks separate, then it is in the open.
        @Override
        public int getBrightness(@NonNull LightLayer layer, @NonNull BlockPos pos) {
            if (pos.equals(current) || region.getBlockState(pos).isAir()) return region.getBrightness(layer, pos);
            if (layer == LightLayer.SKY) return 15;
            int best = 0;
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (region.getBlockState(next).isAir()) best = Math.max(best, region.getBrightness(LightLayer.BLOCK, next));
            }
            return best;
        }

        @Override
        public int getRawBrightness(@NonNull BlockPos pos, int skyDarken) {
            return Math.max(getBrightness(LightLayer.SKY, pos) - skyDarken, getBrightness(LightLayer.BLOCK, pos));
        }

        @Override
        public boolean canSeeSky(@NonNull BlockPos pos) {
            return getBrightness(LightLayer.SKY, pos) >= 15;
        }

        @Override public @NonNull LevelLightEngine getLightEngine() { return region.getLightEngine(); }
        @Override public int getHeight() { return region.getHeight(); }
        @Override public int getMinY() { return region.getMinY(); }
        @Override public @NonNull CardinalLighting cardinalLighting() { return region.cardinalLighting(); }
        @Override public int getBlockTint(@NonNull BlockPos pos, @NonNull ColorResolver resolver) { return region.getBlockTint(pos, resolver); }
    }
}
