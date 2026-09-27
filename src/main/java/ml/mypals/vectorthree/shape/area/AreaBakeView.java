package ml.mypals.vectorthree.shape.area;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
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

/**
 * The source region as if it floated alone in open air: everything outside reads as air, and the light is worked
 * out for the region by itself (full sky light all around it, block light only from its own emitters) instead of
 * read from the world, where the region's surroundings, and the suppression of the region itself, would skew it.
 */
final class AreaBakeView implements BlockAndTintGetter {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockAndTintGetter delegate;
    private final BlockPos min;
    private final BlockPos max;
    // The region plus a one-block shell of air, indexed x + sizeX * (y + sizeY * z) from min - 1.
    private final int sizeX, sizeY, sizeZ;
    private byte[] sky;
    private byte[] block;

    AreaBakeView(BlockAndTintGetter delegate, BlockPos min, BlockPos max) {
        this.delegate = delegate;
        this.min = min;
        this.max = max;
        this.sizeX = max.getX() - min.getX() + 3;
        this.sizeY = max.getY() - min.getY() + 3;
        this.sizeZ = max.getZ() - min.getZ() + 3;
    }

    private boolean inside(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    @Override
    public @NonNull BlockState getBlockState(@NonNull BlockPos pos) {
        return inside(pos) ? delegate.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    @Override
    public @NonNull FluidState getFluidState(@NonNull BlockPos pos) {
        return inside(pos) ? delegate.getFluidState(pos) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(@NonNull BlockPos pos) {
        return inside(pos) ? delegate.getBlockEntity(pos) : null;
    }

    @Override
    public int getBrightness(@NonNull LightLayer layer, @NonNull BlockPos pos) {
        int index = index(pos.getX() - min.getX() + 1, pos.getY() - min.getY() + 1, pos.getZ() - min.getZ() + 1);
        if (index < 0) return layer == LightLayer.SKY ? 15 : 0;
        ensureLight();
        return layer == LightLayer.SKY ? sky[index] : block[index];
    }

    @Override
    public int getRawBrightness(@NonNull BlockPos pos, int skyDarken) {
        return Math.max(getBrightness(LightLayer.SKY, pos) - skyDarken, getBrightness(LightLayer.BLOCK, pos));
    }

    @Override
    public boolean canSeeSky(@NonNull BlockPos pos) {
        return getBrightness(LightLayer.SKY, pos) >= 15;
    }

    private int index(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return -1;
        return x + sizeX * (y + sizeY * z);
    }

    private boolean shell(int x, int y, int z) {
        return x == 0 || y == 0 || z == 0 || x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1;
    }

    // Vanilla's rules, simplified: a step costs max(1, dampening) and full sky light falls straight down through
    // blocks that let it; shape-dependent occlusion (slabs, stairs) is ignored.
    private void ensureLight() {
        if (sky != null) return;
        int count = sizeX * sizeY * sizeZ;
        sky = new byte[count];
        block = new byte[count];
        BlockState[] states = new BlockState[count];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        IntArrayFIFOQueue skyQueue = new IntArrayFIFOQueue();
        IntArrayFIFOQueue blockQueue = new IntArrayFIFOQueue();
        for (int z = 0; z < sizeZ; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = index(x, y, z);
                    if (shell(x, y, z)) {
                        states[index] = Blocks.AIR.defaultBlockState();
                        sky[index] = 15;
                        skyQueue.enqueue(index);
                        continue;
                    }
                    BlockState state = delegate.getBlockState(pos.set(min.getX() + x - 1, min.getY() + y - 1, min.getZ() + z - 1));
                    states[index] = state;
                    int emission = state.getLightEmission();
                    if (emission > 0) {
                        block[index] = (byte) emission;
                        blockQueue.enqueue(index);
                    }
                }
            }
        }
        spread(sky, skyQueue, states, true);
        spread(block, blockQueue, states, false);
    }

    private void spread(byte[] light, IntArrayFIFOQueue queue, BlockState[] states, boolean skyLight) {
        while (!queue.isEmpty()) {
            int index = queue.dequeueInt();
            int level = light[index];
            int x = index % sizeX, y = index / sizeX % sizeY, z = index / (sizeX * sizeY);
            for (Direction direction : DIRECTIONS) {
                int next = index(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
                if (next < 0) continue;
                BlockState state = states[next];
                int value = skyLight && level == 15 && direction == Direction.DOWN && state.propagatesSkylightDown()
                        ? 15 : level - Math.max(1, state.getLightDampening());
                if (value > light[next]) {
                    light[next] = (byte) value;
                    queue.enqueue(next);
                }
            }
        }
    }

    @Override
    public @NonNull LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public @NonNull CardinalLighting cardinalLighting() {
        return delegate.cardinalLighting();
    }

    @Override
    public int getBlockTint(@NonNull BlockPos pos, @NonNull ColorResolver resolver) {
        return delegate.getBlockTint(pos, resolver);
    }
}
