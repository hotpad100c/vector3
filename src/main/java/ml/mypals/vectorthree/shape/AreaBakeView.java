package ml.mypals.vectorthree.shape;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;


final class AreaBakeView implements BlockAndTintGetter {
    private final BlockAndTintGetter delegate;
    private final BlockPos min;
    private final BlockPos max;

    AreaBakeView(BlockAndTintGetter delegate, BlockPos min, BlockPos max) {
        this.delegate = delegate;
        this.min = min;
        this.max = max;
    }

    private boolean inside(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    @Override
    public @NonNull BlockState getBlockState(@NonNull BlockPos pos) {
        return inside(pos) ? delegate.getBlockState(pos) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    public @NonNull FluidState getFluidState(@NonNull BlockPos pos) {
        return inside(pos) ? delegate.getFluidState(pos) : net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(@NonNull BlockPos pos) {
        return inside(pos) ? delegate.getBlockEntity(pos) : null;
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
