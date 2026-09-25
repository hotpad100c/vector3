package ml.mypals.vectorthree.shape.area;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
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
    // Only the thread that bypasses sees through: chunk meshing on worker threads must stay suppressed.
    private static volatile Thread bypassThread;

    private AreaSuppression() {}

    public static boolean isSuppressed(BlockPos pos) {
        if (ACTIVE.isEmpty() || bypassThread == Thread.currentThread()) return false;
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
        Thread previous = bypassThread;
        bypassThread = Thread.currentThread();
        try {
            action.run();
        } finally {
            bypassThread = previous;
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
        if (newBounds.equals(oldBounds)) return;
        if (oldBounds != null) touch(oldBounds);
        touch(newBounds);
        relightChanged(oldBounds, newBounds);
    }

    public static void clear(String shapeId) {
        Bounds bounds = ACTIVE.remove(shapeId);
        DIRTY.remove(shapeId);
        if (bounds != null) {
            touch(bounds);
            relightChanged(bounds, null);
        }
    }

    // The client light engine sees suppressed blocks as air, so sky sources and light around them are redone.
    private static void relightChanged(Bounds a, Bounds b) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Set<Long> chunks = new java.util.HashSet<>();
        for (Bounds bounds : new Bounds[]{a, b}) {
            if (bounds == null) continue;
            for (int cx = (bounds.min().getX() - 1) >> 4; cx <= (bounds.max().getX() + 1) >> 4; cx++) {
                for (int cz = (bounds.min().getZ() - 1) >> 4; cz <= (bounds.max().getZ() + 1) >> 4; cz++) {
                    chunks.add(ChunkPos.pack(cx, cz));
                }
            }
        }
        for (long packed : chunks) {
            int cx = ChunkPos.getX(packed), cz = ChunkPos.getZ(packed);
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk == null) continue;
            int[] before = new int[256];
            for (int i = 0; i < 256; i++) before[i] = chunk.getSkyLightSources().getLowestSourceY(i & 15, i >> 4);
            chunk.initializeLightSources();
            for (Bounds bounds : new Bounds[]{a, b}) {
                if (bounds != null) relight(level, chunk, bounds, before);
            }
        }
    }

    public static void relightChunk(int chunkX, int chunkZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || ACTIVE.isEmpty()) return;
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null) return;
        for (Bounds bounds : ACTIVE.values()) relight(level, chunk, bounds, null);
    }

    private static void relight(ClientLevel level, LevelChunk chunk, Bounds bounds, int[] sourcesBefore) {
        int minX = Math.max(bounds.min().getX() - 1, chunk.getPos().getMinBlockX());
        int maxX = Math.min(bounds.max().getX() + 1, chunk.getPos().getMinBlockX() + 15);
        int minZ = Math.max(bounds.min().getZ() - 1, chunk.getPos().getMinBlockZ());
        int maxZ = Math.min(bounds.max().getZ() + 1, chunk.getPos().getMinBlockZ() + 15);
        if (minX > maxX || minZ > maxZ) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int index = (x & 15) | (z & 15) << 4;
                int source = chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15);
                if (sourcesBefore != null) source = Math.min(source, sourcesBefore[index]);
                int bottom = Math.max(level.getMinY(), Math.min(bounds.min().getY() - 1, source));
                for (int y = bounds.max().getY() + 1; y >= bottom; y--) level.getLightEngine().checkBlock(pos.set(x, y, z));
            }
        }
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
