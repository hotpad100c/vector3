package ml.mypals.vectorthree.shape.area;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class AreaSuppression {
    private record Bounds(BlockPos min, BlockPos max) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    private static final Map<String, Bounds> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<String> DIRTY = ConcurrentHashMap.newKeySet();
    private static boolean bypassing;

    private AreaSuppression() {}

    public static boolean isSuppressed(BlockPos pos) {
        if (bypassing) return false;
        for (Bounds bounds : ACTIVE.values()) {
            if (bounds.contains(pos)) return true;
        }
        return false;
    }

    /**
     * Runs {@code action} with suppression checks disabled. AreaShape's own bake and destination-draw
     * calls go through the exact tesselateBlock/tesselate/submit methods the suppression mixins guard
     * (to hide the source content in-place) — without this, a shape would suppress its own source
     * content while baking or re-drawing block entities, since that content sits inside its own
     * (already-active) suppressed AABB.
     */
    public static void bypassing(Runnable action) {
        boolean previous = bypassing;
        bypassing = true;
        try {
            action.run();
        } finally {
            bypassing = previous;
        }
    }

    /** Marks every shape whose source AABB contains {@code pos} as needing a re-bake. */
    public static void markDirtyIfInside(BlockPos pos) {
        for (Map.Entry<String, Bounds> entry : ACTIVE.entrySet()) {
            if (entry.getValue().contains(pos)) DIRTY.add(entry.getKey());
        }
    }

    /** Returns and clears whether this shape was marked dirty; call from the render thread only. */
    public static boolean consumeDirty(String shapeId) {
        return DIRTY.remove(shapeId);
    }

    /** Marks this shape's AABB as suppressed and forces the affected chunk sections to recompile. */
    public static void set(String shapeId, BlockPos min, BlockPos max) {
        Bounds newBounds = new Bounds(min, max);
        Bounds oldBounds = ACTIVE.put(shapeId, newBounds);
        if (oldBounds != null) touch(oldBounds);
        touch(newBounds);
    }

    public static void clear(String shapeId) {
        Bounds bounds = ACTIVE.remove(shapeId);
        DIRTY.remove(shapeId);
        if (bounds != null) touch(bounds);
    }

    /**
     * ponytail: forces a recompile of every touched chunk section by re-announcing each block's
     * (unchanged) state via the same public API vanilla uses for real block updates, expanded by one
     * block so neighbor-dependent face culling/AO at the boundary also refreshes. No direct
     * "mark section dirty" method was confirmed to exist on LevelRenderer in this build; upgrade to a
     * more targeted call if one turns up and this proves too slow for large areas.
     */
    private static void touch(Bounds bounds) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.min().getX() - 1; x <= bounds.max().getX() + 1; x++) {
            for (int y = bounds.min().getY() - 1; y <= bounds.max().getY() + 1; y++) {
                for (int z = bounds.min().getZ() - 1; z <= bounds.max().getZ() + 1; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }
    }
}
