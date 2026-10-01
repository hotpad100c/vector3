package ml.mypals.vectorthree.mc.light;

import ml.mypals.vectorthree.mc.render.PreparedDraws;
import ml.mypals.vectorthree.mc.render.Pipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.core.light.Light;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Local opaque-block albedo meshes, independent of the active chunk renderer and shader pack. */
final class BlockAlbedoCapture {
    private static final int GRID = 4;
    private static final int MAX_CELLS = 32768;
    private static final int MAX_LIGHTS = 4;
    private static final Map<Key, Cache> CACHES = new LinkedHashMap<>();
    private static RenderType solidType, cutoutType;
    private static ClientLevel world;
    private static int frame;

    private BlockAlbedoCapture() {}

    static boolean prepare() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return false;
        }
        if (world != level) {
            clear();
            world = level;
        }
        frame++;
        Vec3 eye = minecraft.gameRenderer.mainCamera().position();
        List<Light> lights = new ArrayList<>(LightRenderer.activeLights());
        lights.sort(Comparator.comparingDouble(light -> light.position().distanceToSqr(eye)));
        Set<Key> wanted = new HashSet<>();
        int newBuilds = 0;
        for (int i = 0; i < Math.min(MAX_LIGHTS, lights.size()); i++) {
            Key key = bounds(level, lights.get(i));
            if (key == null) continue;
            wanted.add(key);
            Cache cache = CACHES.computeIfAbsent(key, Cache::new);
            if ((cache.dirty || frame - cache.builtFrame > 120) && newBuilds < 1) {
                cache.build();
                cache.builtFrame = frame;
                cache.dirty = false;
                newBuilds++;
            }
        }
        CACHES.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
        return CACHES.values().stream().anyMatch(Cache::hasGeometry);
    }

    static void draw(RenderPass pass) {
        Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
        for (Cache cache : CACHES.values()) cache.draw(pass, eye);
    }

    static void invalidate(BlockPos pos) {
        for (Cache cache : CACHES.values()) {
            Key key = cache.key;
            if (pos.getX() >= key.minX && pos.getX() <= key.maxX
                    && pos.getY() >= key.minY && pos.getY() <= key.maxY
                    && pos.getZ() >= key.minZ && pos.getZ() <= key.maxZ) cache.dirty = true;
        }
    }

    static void invalidateChunk(int chunkX, int chunkZ) {
        int minX = chunkX << 4, minZ = chunkZ << 4;
        for (Cache cache : CACHES.values()) {
            Key key = cache.key;
            if (key.minX <= minX + 15 && key.maxX >= minX
                    && key.minZ <= minZ + 15 && key.maxZ >= minZ) cache.dirty = true;
        }
    }

    static void clear() {
        for (Cache cache : CACHES.values()) cache.close();
        CACHES.clear();
        world = null;
    }

    private static Key bounds(ClientLevel level, Light light) {
        Vec3 center = light.position();
        double ex = light.radius(), ey = ex, ez = ex;
        if (light.type() == Light.Type.AREA) {
            Vec3 axis = light.direction().normalize();
            center = center.add(axis.scale(light.areaReach() * 0.5));
            double halfFace = Math.max(light.areaWidth(), light.areaHeight()) * 0.5;
            ex += Math.abs(axis.x) * light.areaReach() * 0.5 + halfFace;
            ey += Math.abs(axis.y) * light.areaReach() * 0.5 + halfFace;
            ez += Math.abs(axis.z) * light.areaReach() * 0.5 + halfFace;
        }
        int x = Math.floorDiv((int) Math.floor(center.x), GRID) * GRID;
        int y = Math.floorDiv((int) Math.floor(center.y), GRID) * GRID;
        int z = Math.floorDiv((int) Math.floor(center.z), GRID) * GRID;
        int hx = (int) Math.ceil(ex) + GRID, hy = (int) Math.ceil(ey) + GRID, hz = (int) Math.ceil(ez) + GRID;
        long cells = (long) (hx * 2 + 1) * (hy * 2 + 1) * (hz * 2 + 1);
        if (cells > MAX_CELLS) return null;
        return new Key(level, x - hx, y - hy, z - hz, x + hx, y + hy, z + hz);
    }

    private static RenderType type(boolean cutout) {
        if (cutout ? cutoutType != null : solidType != null) return cutout ? cutoutType : solidType;
        RenderPipeline base = cutout ? RenderPipelines.CUTOUT_BLOCK : RenderPipelines.SOLID_BLOCK;
        RenderPipeline.Snippet snippet = Pipelines.snippetOf(base);
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Mod.id(cutout ? "pipeline/block_albedo_cutout" : "pipeline/block_albedo_solid"))
                .withVertexShader(Mod.id("core/block_albedo"))
                .withFragmentShader(Mod.id("core/block_albedo"))
                .build());
        RenderType result = RenderType.create(cutout ? "vector3_block_albedo_cutout" : "vector3_block_albedo_solid",
                RenderSetup.builder(pipeline).withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                        .useLightmap().createRenderSetup());
        if (cutout) cutoutType = result;
        else solidType = result;
        return result;
    }

    private record Key(ClientLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private static final class Cache {
        final Key key;
        final Mesh solid = new Mesh(), cutout = new Mesh();
        boolean dirty = true;
        int builtFrame;

        Cache(Key key) { this.key = key; }

        boolean hasGeometry() { return !solid.empty() || !cutout.empty(); }

        void build() {
            BufferBuilder solidBuilder = new BufferBuilder(new ByteBufferBuilder(2048),
                    type(false).primitiveTopology(), type(false).format());
            BufferBuilder cutoutBuilder = new BufferBuilder(new ByteBufferBuilder(2048),
                    type(true).primitiveTopology(), type(true).format());
            BlockColors colors = Minecraft.getInstance().getBlockColors();
            ModelBlockRenderer renderer = new ModelBlockRenderer(false, true, colors);
            Output output = new Output(solidBuilder, cutoutBuilder, colors, key.level);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = key.minX; x <= key.maxX; x++) {
                for (int z = key.minZ; z <= key.maxZ; z++) {
                    if (!key.level.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y = key.minY; y <= key.maxY; y++) {
                        pos.set(x, y, z);
                        BlockState state = key.level.getBlockState(pos);
                        if (state.isAir()) continue;
                        output.block(state, pos);
                        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
                        renderer.tesselateBlock(output, x - key.minX, y - key.minY, z - key.minZ,
                                key.level, pos, state, model, pos.asLong());
                    }
                }
            }
            solid.upload(solidBuilder.build());
            cutout.upload(cutoutBuilder.build());
        }

        void draw(RenderPass pass, Vec3 eye) {
            if (!hasGeometry()) return;
            Matrix4f transform = new Matrix4f(RenderSystem.getModelViewMatrixCopy())
                    .translate((float) (key.minX - eye.x), (float) (key.minY - eye.y), (float) (key.minZ - eye.z));
            GpuBufferSlice dynamic = RenderSystem.getDynamicUniforms().writeTransform(
                    transform, new Vector4f(1), new Vector3f(), new Matrix4f());
            solid.draw(pass, type(false), dynamic);
            cutout.draw(pass, type(true), dynamic);
        }

        void close() { solid.close(); cutout.close(); }
    }

    private static final class Output implements BlockQuadOutput {
        private final BufferBuilder solid, cutout;
        private final BlockColors colors;
        private final ClientLevel level;
        private BlockState state;
        private BlockPos pos;

        Output(BufferBuilder solid, BufferBuilder cutout, BlockColors colors, ClientLevel level) {
            this.solid = solid;
            this.cutout = cutout;
            this.colors = colors;
            this.level = level;
        }

        void block(BlockState state, BlockPos pos) {
            this.state = state;
            this.pos = pos;
        }

        @Override public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            ChunkSectionLayer layer = quad.materialInfo().layer();
            if (layer == ChunkSectionLayer.TRANSLUCENT) return;
            int tint = -1;
            int index = quad.materialInfo().tintIndex();
            if (index >= 0) {
                List<BlockTintSource> sources = colors.getTintSources(state);
                if (index < sources.size() && sources.get(index) != null)
                    tint = sources.get(index).colorInWorld(state, level, pos);
            }
            instance.setColor(0xFF000000 | (tint & 0xFFFFFF));
            (layer == ChunkSectionLayer.CUTOUT ? cutout : solid).putBlockBakedQuad(x, y, z, quad, instance);
        }
    }

    private static final class Mesh {
        GpuBuffer vertices;
        int indices;
        PrimitiveTopology topology = PrimitiveTopology.TRIANGLES;

        boolean empty() { return vertices == null || indices == 0; }

        void upload(MeshData data) {
            close();
            if (data == null) return;
            try {
                indices = data.drawState().indexCount();
                topology = data.drawState().primitiveTopology();
                vertices = RenderSystem.getDevice().createBuffer(() -> "vector3_block_albedo",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.vertexBuffer());
            } finally {
                data.close();
            }
        }

        void draw(RenderPass pass, RenderType type, GpuBufferSlice dynamic) {
            if (empty()) return;
            PreparedRenderType prepared = PreparedDraws.withTransform(type.prepare(), dynamic);
            PreparedDraws.draw(pass, prepared, vertices, null, null, 0, 0, indices, topology);
        }

        void close() {
            if (vertices != null) vertices.close();
            vertices = null;
            indices = 0;
        }
    }
}
