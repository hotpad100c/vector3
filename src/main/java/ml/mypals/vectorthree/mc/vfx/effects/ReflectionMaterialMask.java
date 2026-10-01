package ml.mypals.vectorthree.mc.vfx.effects;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import ml.mypals.vectorthree.core.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A camera-local block lookup texture for SSR. The scene depth supplies the visible surface position. */
final class ReflectionMaterialMask {
    static final int HORIZONTAL = 48, VERTICAL = 64;
    private static final Identifier TEXTURE_ID = Mod.id("reflection/material_mask");
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final Map<Block, Boolean> MATCH_CACHE = new HashMap<>();
    private static final Set<Block> SELECTED = new HashSet<>();
    private static final List<TagKey<Block>> TAGS = new ArrayList<>();
    private static NativeImage pixels;
    private static DynamicTexture texture;
    private static ClientLevel previousLevel;
    private static String previousSelection;
    private static int originX, originY, originZ;
    private static long updatedAt;
    private static boolean water, glass, ice;

    private ReflectionMaterialMask() {}

    static GpuTextureView view(ClientLevel level, BlockPos camera, String selection) {
        if (level == null || selection == null || selection.isBlank()) return null;
        boolean selectionChanged = !selection.equals(previousSelection);
        if (selectionChanged) parse(selection);
        if (!water && !glass && !ice && SELECTED.isEmpty() && TAGS.isEmpty()) return null;
        long now = System.nanoTime();
        boolean moved = Math.abs(camera.getX() - originX - HORIZONTAL / 2) > 8
                || Math.abs(camera.getY() - originY - VERTICAL / 2) > 8
                || Math.abs(camera.getZ() - originZ - HORIZONTAL / 2) > 8;
        if (texture == null) {
            pixels = new NativeImage(HORIZONTAL * HORIZONTAL, VERTICAL, false);
            texture = new DynamicTexture(() -> "Vector3 reflection materials", pixels);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
        }
        if (selectionChanged || previousLevel != level || moved || now - updatedAt > 1_000_000_000L) {
            if (previousLevel != level) MATCH_CACHE.clear();
            if (previousLevel != level || moved || updatedAt == 0) {
                originX = camera.getX() - HORIZONTAL / 2;
                originY = camera.getY() - VERTICAL / 2;
                originZ = camera.getZ() - HORIZONTAL / 2;
            }
            fill(level);
            texture.upload();
            previousLevel = level;
            updatedAt = now;
        }
        return texture.getTextureView();
    }

    static int originX() { return originX; }
    static int originY() { return originY; }
    static int originZ() { return originZ; }

    private static void parse(String selection) {
        water = glass = ice = false;
        SELECTED.clear();
        TAGS.clear();
        MATCH_CACHE.clear();
        for (String token : selection.split("[,;\\s]+")) {
            if (token.isBlank()) continue;
            switch (token.toLowerCase(java.util.Locale.ROOT)) {
                case "water" -> water = true;
                case "glass" -> glass = true;
                case "ice" -> ice = true;
                default -> {
                    boolean tag = token.startsWith("#");
                    Identifier id = Identifier.tryParse(tag ? token.substring(1) : token);
                    if (id == null) continue;
                    if (tag) TAGS.add(TagKey.create(Registries.BLOCK, id));
                    else if (BuiltInRegistries.BLOCK.keySet().contains(id))
                        SELECTED.add(BuiltInRegistries.BLOCK.getValue(id));
                }
            }
        }
        previousSelection = selection;
    }

    private static boolean selected(BlockState state) {
        Block block = state.getBlock();
        return MATCH_CACHE.computeIfAbsent(block, key -> {
            if (SELECTED.contains(key) || water && key == Blocks.WATER) return true;
            String path = BuiltInRegistries.BLOCK.getKey(key).getPath();
            if (glass && (path.equals("glass") || path.endsWith("_glass")
                    || path.equals("glass_pane") || path.endsWith("_glass_pane"))) return true;
            if (ice && (path.equals("ice") || path.endsWith("_ice"))) return true;
            for (TagKey<Block> tag : TAGS) if (state.is(tag)) return true;
            return false;
        });
    }

    private static void fill(ClientLevel level) {
        int bottom = level.getMinY(), top = level.getMaxY();
        for (int z = 0; z < HORIZONTAL; z++) for (int x = 0; x < HORIZONTAL; x++) {
            int worldX = originX + x, worldZ = originZ + z;
            POS.set(worldX, originY, worldZ);
            boolean loaded = level.hasChunkAt(POS);
            int pixelX = x + z * HORIZONTAL;
            for (int y = 0; y < VERTICAL; y++) {
                int worldY = originY + y;
                boolean reflective = false;
                if (loaded && worldY >= bottom && worldY < top) {
                    POS.set(worldX, worldY, worldZ);
                    reflective = selected(level.getBlockState(POS));
                }
                pixels.setPixel(pixelX, y, reflective ? 0xFFFFFFFF : 0xFF000000);
            }
        }
    }

    static void clear() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
        texture = null;
        pixels = null;
        previousLevel = null;
        updatedAt = 0;
        MATCH_CACHE.clear();
    }
}
