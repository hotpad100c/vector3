package ml.mypals.vectorthree.mc.shape.model;

import ml.mypals.vectorthree.core.Mod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.tags.EmptyMesh;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.transform.shapeTransformers.DefaultTransformer;
import ml.mypals.vectorthree.core.pose.EntityPose;
import ml.mypals.vectorthree.core.pose.PartTree;
import ml.mypals.vectorthree.mc.camera.PreviewPass;
import ml.mypals.vectorthree.mc.compat.IrisCompat;
import ml.mypals.vectorthree.mc.pose.EntityPoses;
import ml.mypals.vectorthree.mc.shape.entity.ShapeEntities;
import ml.mypals.vectorthree.mc.render.IrisBypassTarget;
import ml.mypals.vectorthree.mc.render.ScreenLayer;
import ml.mypals.vectorthree.core.shape.ShapeState;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.core.shape.media.ImageDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.OptionalDouble;

/**
 * An OBJ model kept on the GPU (see {@link ObjModel} for parsing): uploaded once, and each frame only a transform
 * and colour per material are written. Solid colour, one chosen texture, or the model's own MTL materials.
 * <p>
 * RRK still owns the shape (manager, hierarchy, picking), but its per-frame vertex path is skipped: the mesh is
 * prepared in LevelRenderer#submitFeatures and drawn inside the main pass, like AreaShape (see AreaMainPassMixin).
 * With a shader pack it either bypasses Iris like ImageShape (the shape's "bypass shaders" option), or goes through
 * Iris's entity program with an entity-format copy of the mesh.
 */
public final class TexturedObjShape extends ObjModelShape implements EmptyMesh {
    public static final String MODE = "vector3:obj_mode";
    public static final String TEXTURE = "vector3:obj_texture";
    public static final String LIGHTING = "vector3:obj_lighting";
    /** Pivot overrides for posing: {@code part=x,y,z;part=x,y,z}. */
    public static final String PIVOTS = "vector3:obj_pivots";
    /** Per-part texture overrides, a path or empty: {@code part=path;part=path}. */
    public static final String PART_TEXTURES = "vector3:obj_part_textures";
    /** Per-part normal map overrides, a path or empty: {@code part=path;part=path}. */
    public static final String PART_NORMALS = "vector3:obj_part_normals";
    /** Per-part specular map overrides, a path or empty: {@code part=path;part=path}. */
    public static final String PART_SPECULARS = "vector3:obj_part_speculars";
    /** Per-part colour tints, ARGB ints: {@code part=argb;part=argb}. */
    public static final String PART_COLORS = "vector3:obj_part_colors";
    /** Hidden parts only, {@code false} being hidden: {@code part=false;part=false}. */
    public static final String PART_VISIBLE = "vector3:obj_part_visible";
    private static final String DEFAULT_MODEL = "ryansrenderingkit:models/monkey.obj";
    private static final Identifier WHITE = Mod.id("obj/white");
    private record TextureKey(ObjModel.TextureRef texture, @Nullable ObjModel.TextureRef normal,
            @Nullable ObjModel.TextureRef specular) {}

    /** A part's resolved overrides: the maps to swap in (null keeps the range's own) and the colour to multiply. */
    private record PartOverride(@Nullable ObjModel.TextureRef texture, @Nullable ObjModel.TextureRef normal,
            @Nullable ObjModel.TextureRef specular, float red, float green, float blue, float alpha) {
        static final PartOverride NONE = new PartOverride(null, null, null, 1, 1, 1, 1);

        boolean empty() {
            return texture == null && normal == null && specular == null
                    && red == 1 && green == 1 && blue == 1 && alpha == 1;
        }
    }

    private static final Map<TextureKey, Identifier> TEXTURES = new HashMap<>();
    private static final List<TexturedObjShape> PREPARED = new ArrayList<>();
    private static RenderPipeline entityPipeline, entitySeeThroughPipeline;
    private static RenderPipeline shadowEntityPipeline;
    private static boolean whiteLoaded;

    public enum Mode {
        SOLID("solid"), TEXTURE("texture"), MATERIALS("mtl");

        public final String id;

        Mode(String id) {
            this.id = id;
        }

        public static Mode of(@Nullable String id) {
            for (Mode mode : values()) if (mode.id.equals(id)) return mode;
            return SOLID;
        }
    }

    private record FrameDraw(PreparedRenderType type, int first, int count, boolean translucent) {}

    private final Mode mode;
    private final @Nullable String texturePath;
    private final boolean lighting;
    private final String shapeId;
    private Map<String, Vector3f> pivots;
    private Map<String, String> partTextures = Map.of();
    private Map<String, String> partNormals = Map.of();
    private Map<String, String> partSpeculars = Map.of();
    private Map<String, Integer> partColors = Map.of();
    private Set<String> hiddenParts = Set.of();
    // Resolved once per part: Material.of and the _n / _s companions check the disk, which no frame should repeat.
    private final Map<Integer, PartOverride> partOverrides = new HashMap<>();
    private ObjModel model;
    private GpuBuffer gpuVertices;
    private GpuBuffer gpuIndices;
    private GpuBuffer entityVertices;
    private Boolean entityExtended;
    private boolean failed;
    private boolean frameEntity;
    // The packed light the entity mesh was last built with. The format carries one light value per vertex, so a
    // shape whose position's light changed has to be rebuilt; the initial value is the full-bright constant the
    // mesh used to be baked with.
    private int bakedLight = 0xF000F0;
    private final List<FrameDraw> frameDraws = new ArrayList<>();
    // The shadow pass runs before the main pass, from the sun: its frames are a second set, never a reuse of the
    // main pass's (which hold the main camera's matrices and the entity pipelines).
    private final List<FrameDraw> shadowDraws = new ArrayList<>();

    public static Mode mode(ShapeState state) {
        return Mode.of(state.blockProperties() == null ? null : state.blockProperties().get(MODE));
    }

    public static @Nullable String texturePath(ShapeState state) {
        return state.blockProperties() == null ? null : state.blockProperties().get(TEXTURE);
    }

    public static boolean lighting(ShapeState state) {
        return state.blockProperties() != null && "true".equals(state.blockProperties().get(LIGHTING));
    }

    public static Map<String, Vector3f> pivots(@Nullable Map<String, String> properties) {
        Map<String, Vector3f> result = new LinkedHashMap<>();
        String text = properties == null ? null : properties.get(PIVOTS);
        if (text == null) return result;
        for (String entry : text.split(";")) {
            int equals = entry.lastIndexOf('=');
            String[] xyz = equals < 0 ? new String[0] : entry.substring(equals + 1).split(",");
            if (xyz.length != 3) continue;
            try {
                result.put(entry.substring(0, equals), new Vector3f(Float.parseFloat(xyz[0]), Float.parseFloat(xyz[1]),
                        Float.parseFloat(xyz[2])));
            } catch (NumberFormatException ignored) {
                // A malformed entry only loses that pivot.
            }
        }
        return result;
    }

    public static String encodePivots(Map<String, Vector3f> pivots) {
        StringBuilder text = new StringBuilder();
        pivots.forEach((name, pivot) -> text.append(text.isEmpty() ? "" : ";").append(name).append('=')
                .append(pivot.x).append(',').append(pivot.y).append(',').append(pivot.z));
        return text.toString();
    }

    public static Map<String, String> partTextures(@Nullable Map<String, String> properties) {
        return partPaths(properties, PART_TEXTURES);
    }

    public static Map<String, String> partNormals(@Nullable Map<String, String> properties) {
        return partPaths(properties, PART_NORMALS);
    }

    public static Map<String, String> partSpeculars(@Nullable Map<String, String> properties) {
        return partPaths(properties, PART_SPECULARS);
    }

    /** The part's ARGB tint, or the empty map when none is set. Entries that aren't integers are dropped. */
    public static Map<String, Integer> partColors(@Nullable Map<String, String> properties) {
        Map<String, Integer> result = new LinkedHashMap<>();
        partPaths(properties, PART_COLORS).forEach((part, color) -> {
            try {
                result.put(part, (int) Long.parseLong(color.trim()));
            } catch (NumberFormatException ignored) {
                // A malformed entry only loses that colour.
            }
        });
        return result;
    }

    /** Parts the model hides; the format only stores them, so anything missing is visible. */
    public static Set<String> hiddenParts(@Nullable Map<String, String> properties) {
        Set<String> result = new LinkedHashSet<>();
        partPaths(properties, PART_VISIBLE).forEach((part, visible) -> {
            if ("false".equalsIgnoreCase(visible.trim())) result.add(part);
        });
        return result;
    }

    public static String encodePartTextures(Map<String, String> map) {
        return encodeParts(map);
    }

    public static String encodePartNormals(Map<String, String> map) {
        return encodeParts(map);
    }

    public static String encodePartSpeculars(Map<String, String> map) {
        return encodeParts(map);
    }

    public static String encodePartColors(Map<String, Integer> map) {
        return encodeParts(map);
    }

    public static String encodeHiddenParts(Set<String> hidden) {
        StringBuilder text = new StringBuilder();
        for (String part : hidden) text.append(text.isEmpty() ? "" : ";").append(part).append("=false");
        return text.toString();
    }

    // The "part=value" text the live per-part properties share; the value may hold '=' itself.
    private static Map<String, String> partPaths(@Nullable Map<String, String> properties, String key) {
        Map<String, String> result = new LinkedHashMap<>();
        String text = properties == null ? null : properties.get(key);
        if (text == null) return result;
        for (String entry : text.split(";")) {
            int equals = entry.lastIndexOf('=');
            if (equals < 0) continue;
            result.put(entry.substring(0, equals), entry.substring(equals + 1));
        }
        return result;
    }

    private static String encodeParts(Map<String, ?> entries) {
        StringBuilder text = new StringBuilder();
        entries.forEach((part, value) -> text.append(text.isEmpty() ? "" : ";").append(part).append('=')
                .append(value));
        return text.toString();
    }

    public void setPivots(Map<String, Vector3f> pivots) {
        this.pivots = pivots;
    }

    /** The live per-part overrides, applied without rebuilding the shape; drops the resolved cache. */
    public void setPartConfig(Map<String, String> textures, Map<String, String> normals,
            Map<String, String> speculars, Map<String, Integer> colors, Set<String> hidden) {
        // A state is re-applied many times over with the same values; only a real change is worth another look at
        // the disk. The copies keep the cache honest if a caller mutates a map it handed over.
        Map<String, String> nextTextures = copy(textures);
        Map<String, String> nextNormals = copy(normals);
        Map<String, String> nextSpeculars = copy(speculars);
        Map<String, Integer> nextColors = copy(colors);
        Set<String> nextHidden = hidden == null ? Set.of() : new LinkedHashSet<>(hidden);
        if (nextTextures.equals(partTextures) && nextNormals.equals(partNormals)
                && nextSpeculars.equals(partSpeculars) && nextColors.equals(partColors)
                && nextHidden.equals(hiddenParts)) return;
        this.partTextures = nextTextures;
        this.partNormals = nextNormals;
        this.partSpeculars = nextSpeculars;
        this.partColors = nextColors;
        this.hiddenParts = nextHidden;
        this.partOverrides.clear();
    }

    private static <V> Map<String, V> copy(@Nullable Map<String, V> map) {
        return map == null ? Map.of() : new LinkedHashMap<>(map);
    }

    /**
     * Whether the model's looks come from a map rather than from the shape's colour: the MTL's own materials, or a
     * texture picked in the editor. The colour stays in the shape's state (the editor keeps it for the solid mode),
     * but it no longer takes part in rendering, so anything that reads it must read {@link #tint()} instead.
     */
    private boolean textured() {
        return mode == Mode.MATERIALS
                || (mode == Mode.TEXTURE && texturePath != null && !texturePath.isBlank());
    }

    /**
     * The colour a draw multiplies its material by: none in the textured modes, where a leftover colour would tint
     * the texture the user picked, and the shape's own colour everywhere else.
     */
    private Color tint() {
        return textured() ? Color.WHITE : baseColor;
    }

    /**
     * The packed light of the shape's position in the world, block light and sky light together, read the way vanilla
     * reads it for an entity (see EntityRenderer#getPackedLightCoords). Sampling the world rather than handing the
     * mesh the full-bright constant is what lets a model go dark at night. Outside a level there is nothing to
     * sample, so the old full-bright value stands.
     */
    private int worldLight() {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0xF000F0;
        // The pivot is where the model sits; its origin is sampled rather than a corner of its bounding box, the same
        // choice the vanilla entity path makes with the entity's own position.
        Vec3 pos = transformer.getShapeWorldPivot(true);
        BlockPos blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
        // Block light first: LightCoordsUtil#pack takes (block, sky).
        return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, blockPos),
                level.getBrightness(LightLayer.SKY, blockPos));
    }

    public List<String> partNames() {
        return model.parts.stream().map(ObjModel.Part::name).toList();
    }

    public PartTree partTree() {
        PartTree.Builder builder = PartTree.builder();
        for (ObjModel.Part part : model.parts) builder.add(part.name(), part.parent());
        return builder.build();
    }

    /** The part's pivot: the override if there is one, else the centre of its faces. */
    public Vector3f pivotOf(String part) {
        Vector3f override = pivots.get(part);
        if (override != null) return new Vector3f(override);
        for (ObjModel.Part candidate : model.parts) if (candidate.name().equals(part)) return new Vector3f(candidate.center());
        return new Vector3f();
    }

    public TexturedObjShape(String shapeId, @Nullable Map<String, String> properties, String modelPath, Mode mode,
            @Nullable String texturePath, boolean lighting, Color color, boolean seeThrough) {
        super(Shape.RenderingType.BATCH, color, seeThrough);
        this.transformer = new DefaultTransformer(this, Vec3.ZERO);
        this.transformFunction = transformer -> {};
        this.mode = mode;
        this.texturePath = texturePath;
        this.lighting = lighting;
        this.shapeId = shapeId;
        this.pivots = pivots(properties);
        this.partTextures = partTextures(properties);
        this.partNormals = partNormals(properties);
        this.partSpeculars = partSpeculars(properties);
        this.partColors = partColors(properties);
        this.hiddenParts = hiddenParts(properties);
        String source = modelPath == null || modelPath.isBlank() ? DEFAULT_MODEL : modelPath;
        try {
            model = ObjModel.load(source);
        } catch (Exception exception) {
            failed = true;
            model = new ObjModel();
            Mod.LOGGER.warn("Could not load OBJ {}", source, exception);
        }
        // Picking, bounds and wireframes use RRK's position/index view of the same triangles.
        modelVertexes = List.copyOf(model.positions);
        indexBuffer = new int[model.corners.size()];
        for (int i = 0; i < indexBuffer.length; i++) indexBuffer[i] = model.corners.get(i).position();
        if (!failed && !model.corners.isEmpty()) upload();
        syncLastToTarget();
    }

    private void upload() {
        Map<ObjModel.Corner, Integer> unique = new HashMap<>();
        List<ObjModel.Corner> vertices = new ArrayList<>();
        int[] indices = new int[model.corners.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = unique.computeIfAbsent(model.corners.get(i), corner -> {
                vertices.add(corner);
                return vertices.size() - 1;
            });
        }
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, vertices.size() * 24))) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (ObjModel.Corner corner : vertices) {
                Vec3 p = model.positions.get(corner.position());
                float[] uv = model.uv(corner);
                builder.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(uv[0], uv[1]).setColor(0xFFFFFFFF);
            }
            MeshData mesh = builder.build();
            if (mesh == null) return;
            try {
                gpuVertices = RenderSystem.getDevice().createBuffer(() -> "vector3_obj/vertex", GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
            } finally {
                mesh.close();
            }
        }
        ByteBuffer indexBytes = MemoryUtil.memAlloc(indices.length * Integer.BYTES);
        try {
            indexBytes.asIntBuffer().put(indices);
            gpuIndices = RenderSystem.getDevice().createBuffer(() -> "vector3_obj/index", GpuBuffer.USAGE_INDEX, indexBytes);
        } finally {
            MemoryUtil.memFree(indexBytes);
        }
    }

    // Built during the level render so Iris lays out its extended entity format (tangents, mid-UV), which its
    // entity program expects when the buffer is bound. Plain triangles, three vertices each and in corner order,
    // so material ranges index it directly and Iris's per-primitive tangents come out right.
    private void buildEntityMesh() {
        if (entityVertices != null) entityVertices.close();
        entityVertices = null;
        entityExtended = irisExtendsNow();
        List<ObjModel.Corner> corners = model.corners;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, corners.size() * 64))) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);
            Vector3f face = new Vector3f();
            for (int i = 0; i + 2 < corners.size(); i += 3) {
                Vec3 a = model.positions.get(corners.get(i).position());
                Vec3 b = model.positions.get(corners.get(i + 1).position());
                Vec3 c = model.positions.get(corners.get(i + 2).position());
                new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z))
                        .cross((float) (c.x - a.x), (float) (c.y - a.y), (float) (c.z - a.z), face);
                if (face.lengthSquared() > 0) face.normalize(); else face.set(0, 1, 0);
                for (int k = 0; k < 3; k++) {
                    ObjModel.Corner corner = corners.get(i + k);
                    Vec3 p = model.positions.get(corner.position());
                    float[] uv = model.uv(corner);
                    Vector3f normal = model.normal(corner);
                    Vector3f n = normal != null ? normal : face;
                    builder.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(0xFFFFFFFF).setUv(uv[0], uv[1])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(bakedLight).setNormal(n.x, n.y, n.z);
                }
            }
            MeshData mesh = builder.build();
            try (mesh) {
                if (mesh == null) return;
                entityVertices = RenderSystem.getDevice().createBuffer(() -> "vector3_obj/entity_vertex",
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }

    @Override protected void generateRawGeometry(boolean regenerate) {}

    // With a shader pack and bypassShaders set, Iris would still swap our pipeline for its entity program,
    // which needs normals and light data this mesh lacks (it renders black). Like ImageShape, it then draws with
    // Iris bypassed into IrisBypassTarget, from RRK's END_MAIN draw where no render pass is open; the target is
    // blended onto the main target when the level finishes.
    @Override
    protected void drawInternal(VertexBuilder builder) {
        if (ScreenLayer.isRendering()) {
            drawOnScreen();
            return;
        }
        if (frameDraws.isEmpty() || inMainPass()) return;
        IrisBypassTarget.beginIrisBypass();
        boolean skip = IrisCompat.skipExtension();
        IrisCompat.setSkipExtension(true);
        try {
            var target = IrisBypassTarget.prepareForDraw("Obj");
            assert target.getColorTextureView() != null;
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "vector3_obj",
                    target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                draw(pass, false);
                draw(pass, true);
            }
        } finally {
            IrisCompat.setSkipExtension(skip);
            IrisBypassTarget.endIrisBypass();
        }
    }

    public static void beginFrame() {
        for (TexturedObjShape shape : PREPARED) shape.frameDraws.clear();
        PREPARED.clear();
    }

    /** Called from LevelRenderer#submitFeatures, before any render pass is open. */
    public void submitFrame() {
        frameDraws.clear();
        if (failed || gpuVertices == null || !enabled() || tint().getAlpha() == 0 || onScreenLayer()) return;
        try {
            // Unless the shape bypasses shaders (ShapeState#bypassShaders), a shader pack shades it, which goes
            // through Iris's entity program. Without a pack, "lighting" asks for that same entity path: 26.3's
            // entity shader reads the lightmap, so the mesh is lit by the light where the shape sits and goes dark
            // at night, where obj_lit would keep its fixed direction lit. No pack means irisExtendsNow() is false
            // and IrisCompat.assignPipeline is a no-op, so the entity render type reaches the vanilla entity shader
            // unchanged; nothing else has to adapt for this path.
            frameEntity = (IrisBypassTarget.isActive()
                    && !Boolean.TRUE.equals(customData.get(IrisBypassTarget.SHAPE_FLAG)))
                    || (!IrisCompat.isPackInUse() && lighting);
            if (frameEntity) {
                // One light value covers the whole mesh, so a shape whose position's light changed is rebuilt; the
                // extended flag covers Iris widening the format when a pack is enabled.
                int light = worldLight();
                if (entityVertices == null || entityExtended != irisExtendsNow() || light != bakedLight) {
                    bakedLight = light;
                    buildEntityMesh();
                }
                if (entityVertices == null) return;
                var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                sequential.requestIndexCount(model.corners.size());
                sequential.resizeToRequestedIndexCount();
            }
            prepareDraws(new Matrix4f(RenderSystem.getModelViewMatrixCopy()), true);
            PREPARED.add(this);
        } catch (Exception exception) {
            failed = true;
            frameDraws.clear();
            Mod.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
        }
    }

    private void prepareDraws(Matrix4f base, boolean publishFrames) {
        PoseStack poseStack = new PoseStack();
        beforeDraw(poseStack, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        Matrix4f[] world = partMatrices(pose, publishFrames);
        ObjModel.Material chosen = mode == Mode.TEXTURE ? chosenMaterial() : ObjModel.Material.DEFAULT;
        // One draw for the whole mesh is only right while no part changes what the ranges would use.
        if (world == null && mode != Mode.MATERIALS && noPartOverrides() && hiddenParts.isEmpty()) {
            addDraw(new Matrix4f(base).mul(pose), pose, chosen, 0, model.corners.size());
            return;
        }
        for (ObjModel.Range range : model.ranges) {
            String part = partName(range.part());
            if (part != null && hiddenParts.contains(part)) continue;
            Matrix4f partPose = world == null ? pose : new Matrix4f(pose).mul(world[range.part()]);
            ObjModel.Material material = mode == Mode.MATERIALS ? range.material() : chosen;
            addDraw(new Matrix4f(base).mul(partPose), partPose, partMaterial(material, range.part()), range.first(),
                    range.count());
        }
    }

    private boolean noPartOverrides() {
        return partTextures.isEmpty() && partNormals.isEmpty() && partSpeculars.isEmpty() && partColors.isEmpty();
    }

    private @Nullable String partName(int part) {
        return part >= 0 && part < model.parts.size() ? model.parts.get(part).name() : null;
    }

    // The range's own material with its part's overrides layered on. A per-part texture takes over the part's whole
    // diffuse look: the MTL material's Kd colour and maps no longer tint or replace it, so the texture wins outright.
    private ObjModel.Material partMaterial(ObjModel.Material base, int part) {
        PartOverride override = partOverride(part);
        if (override.empty()) return base;
        boolean maps = mode != Mode.SOLID;
        if (maps && override.texture() != null) {
            return new ObjModel.Material(base.name(), override.red(), override.green(), override.blue(),
                    base.alpha() * override.alpha(), override.texture(), override.normal(), override.specular());
        }
        return new ObjModel.Material(base.name(), base.red() * override.red(), base.green() * override.green(),
                base.blue() * override.blue(), base.alpha() * override.alpha(), base.texture(),
                maps && override.normal() != null ? override.normal() : base.normal(),
                maps && override.specular() != null ? override.specular() : base.specular());
    }

    // Cached per part index: the same part serves several ranges, and resolving a path reads the disk.
    private PartOverride partOverride(int part) {
        PartOverride cached = partOverrides.get(part);
        if (cached != null) return cached;
        String name = partName(part);
        PartOverride override = name == null ? PartOverride.NONE : resolveOverride(name);
        partOverrides.put(part, override);
        return override;
    }

    private PartOverride resolveOverride(String part) {
        ObjModel.TextureRef texture = null, normal = null, specular = null;
        String path = partTextures.get(part);
        if (path != null && !path.isBlank()) {
            ObjModel.TextureRef ref = textureRef(path);
            if (ref != null) {
                ObjModel.Material material = ObjModel.Material.of(ref);
                texture = material.texture();
                normal = material.normal();
                specular = material.specular();
            }
        }
        String normalPath = partNormals.get(part);
        if (normalPath != null && !normalPath.isBlank()) {
            ObjModel.TextureRef ref = textureRef(normalPath);
            if (ref != null) normal = ref;
        }
        String specularPath = partSpeculars.get(part);
        if (specularPath != null && !specularPath.isBlank()) {
            ObjModel.TextureRef ref = textureRef(specularPath);
            if (ref != null) specular = ref;
        }
        float red = 1, green = 1, blue = 1, alpha = 1;
        Integer color = partColors.get(part);
        if (color != null) {
            red = ((color >>> 16) & 255) / 255f;
            green = ((color >>> 8) & 255) / 255f;
            blue = (color & 255) / 255f;
            alpha = ((color >>> 24) & 255) / 255f;
        }
        PartOverride override = new PartOverride(texture, normal, specular, red, green, blue, alpha);
        return override.empty() ? PartOverride.NONE : override;
    }

    // A path from the editor, made absolute against the game directory the way the shape's own texture is.
    private static @Nullable ObjModel.TextureRef textureRef(String path) {
        try {
            Path file = Path.of(path);
            if (!file.isAbsolute()) file = Minecraft.getInstance().gameDirectory.toPath().resolve(file);
            return new ObjModel.TextureRef(file.toAbsolutePath().normalize(), null);
        } catch (RuntimeException ignored) {
            // Not a usable path; the part keeps the texture it would have had.
            return null;
        }
    }

    // Each part's matrix in model space, or null when nothing poses the shape. A part turns about its pivot (plus
    // its offset) inside its parent's frame; the poses stack in track order like an entity's.
    private @Nullable Matrix4f[] partMatrices(Matrix4f pose, boolean publishFrames) {
        UUID uuid = ShapeEntities.uuidOf(shapeId);
        List<EntityPose> poses = EntityPoses.get(uuid);
        boolean watched = publishFrames && EntityPoses.isWatched(uuid) && !PreviewPass.isRendering()
                && !IrisCompat.isRenderingShadowPass();
        if (poses.isEmpty() && !watched) return null;
        int count = model.parts.size();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < count; i++) index.put(model.parts.get(i).name(), i);
        float[][] rotation = new float[count][3], offset = new float[count][3];
        for (EntityPose entityPose : poses) {
            boolean absolute = entityPose.mode() == EntityPose.Mode.ABSOLUTE;
            for (Map.Entry<String, EntityPose.Limb> entry : entityPose.parts().entrySet()) {
                Integer at = index.get(entry.getKey());
                if (at == null) continue;
                EntityPose.Limb limb = entry.getValue();
                if (limb.rotate()) {
                    float[] target = {limb.xRot(), limb.yRot(), limb.zRot()};
                    for (int k = 0; k < 3; k++) rotation[at][k] = absolute ? target[k] : rotation[at][k] + target[k];
                }
                if (limb.move()) {
                    float[] target = {limb.x(), limb.y(), limb.z()};
                    for (int k = 0; k < 3; k++) offset[at][k] = absolute ? target[k] : offset[at][k] + target[k];
                }
            }
        }
        Matrix4f[] world = new Matrix4f[count];
        Map<String, EntityPoses.Frame> frames = watched ? new LinkedHashMap<>() : null;
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        boolean[] visiting = new boolean[count];
        for (int i = 0; i < count; i++) world(i, world, visiting, rotation, offset, pose, frames, camera);
        if (watched) EntityPoses.publishFrames(uuid, frames);
        return world;
    }

    private Matrix4f world(int i, Matrix4f[] world, boolean[] visiting, float[][] rotation, float[][] offset,
            Matrix4f pose, @Nullable Map<String, EntityPoses.Frame> frames, Vec3 camera) {
        if (world[i] != null) return world[i];
        ObjModel.Part part = model.parts.get(i);
        int parentIndex = part.parent();
        visiting[i] = true;
        Matrix4f parent = parentIndex < 0 || visiting[parentIndex] ? new Matrix4f()
                : world(parentIndex, world, visiting, rotation, offset, pose, frames, camera);
        visiting[i] = false;
        Vector3f pivot = pivotOf(part.name());
        float x = rotation[i][0] * Mth.DEG_TO_RAD, y = rotation[i][1] * Mth.DEG_TO_RAD, z = rotation[i][2] * Mth.DEG_TO_RAD;
        world[i] = new Matrix4f(parent).translate(pivot.x + offset[i][0], pivot.y + offset[i][1], pivot.z + offset[i][2])
                .rotateZYX(z, y, x).translate(-pivot.x, -pivot.y, -pivot.z);
        if (frames != null) {
            frames.put(part.name(), EntityPoses.frame(new Matrix4f(pose).mul(parent),
                    new Vector3f(pivot.x + offset[i][0], pivot.y + offset[i][1], pivot.z + offset[i][2]),
                    rotation[i], offset[i], camera));
        }
        return world[i];
    }

    private boolean onScreenLayer() {
        return Boolean.TRUE.equals(customData.get(ScreenLayer.SHAPE_FLAG));
    }

    // On the UI layer the mesh skips the level's main pass and is drawn here, with the UI pass's ortho projection.
    private void drawOnScreen() {
        frameDraws.clear();
        // tint(), not baseColor: in the textured modes the colour no longer takes part, so an invisible leftover
        // colour must not hide a shape the main pass still draws.
        if (failed || gpuVertices == null || tint().getAlpha() == 0) return;
        try {
            frameEntity = false;
            prepareDraws(ScreenLayer.basePose(), false);
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "vector3_obj_screen",
                    target.getColorTextureView(), Optional.empty(), target.hasDepth() ? target.getDepthTextureView() : null,
                    OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                draw(pass, false);
                draw(pass, true);
            }
        } catch (Exception exception) {
            failed = true;
            Mod.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
        } finally {
            frameDraws.clear();
        }
    }

    // core/obj_lit reads the shape's own transform from TextureMat to light faces in world orientation. It is never
    // used while a shader pack is loaded, even when the shape bypasses it: the pack does the lighting then. In the
    // level's main pass "lighting" now takes the entity path (see submitFrame), so lit stays false there and obj_lit
    // is no longer selected; the UI layer (drawOnScreen) still draws through it when lighting is on without a pack,
    // so the branch stays as it was.
    private void addDraw(Matrix4f modelView, Matrix4f pose, ObjModel.Material material, int first, int count) {
        float inverse = 1f / 255f;
        Color base = tint();
        Vector4f color = new Vector4f(base.getRed() * inverse * material.red(),
                base.getGreen() * inverse * material.green(), base.getBlue() * inverse * material.blue(),
                base.getAlpha() * inverse * material.alpha());
        if (color.w <= 0) return;
        boolean lit = lighting && !frameEntity && !IrisCompat.isPackInUse();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(),
                lit ? pose : new Matrix4f());
        Identifier texture = texture(material);
        PreparedRenderType original = (frameEntity ? entityType(texture, seeThrough)
                : ShapeTrackRegistry.objType(texture, seeThrough, lit)).prepare();
        frameDraws.add(new FrameDraw(new PreparedRenderType(original.name(), original.pipeline(),
                original.oitPipelineSet(), transform, original.scissorState(), original.textures()),
                first, count, seeThrough || color.w < 1));
    }

    public static void drawPreparedOpaque(RenderPass pass) {
        for (TexturedObjShape shape : PREPARED) {
            if (shape.inMainPass()) shape.draw(pass, false);
        }
        markShadedDepth();
    }

    public static void drawPreparedTranslucent(RenderPass pass) {
        for (TexturedObjShape shape : PREPARED) {
            if (shape.inMainPass()) shape.draw(pass, true);
        }
        markShadedDepth();
    }

    // Shaded models wrote the main depth; shapes drawn later into the bypass target must see it.
    private static void markShadedDepth() {
        for (TexturedObjShape shape : PREPARED) {
            if (shape.frameEntity && !shape.frameDraws.isEmpty()) {
                IrisBypassTarget.markMainDepthChanged();
                return;
            }
        }
    }

    public static void drawPreparedTranslucent() {
        if (PREPARED.stream().noneMatch(shape -> shape.inMainPass()
                && shape.frameDraws.stream().anyMatch(FrameDraw::translucent))) return;
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "vector3_obj",
                target.getColorTextureView(), Optional.empty(), target.hasDepth() ? target.getDepthTextureView() : null,
                OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            drawPreparedTranslucent(pass);
        }
    }

    // Without a pack, or through Iris's entity program, the mesh is drawn inside the main pass; otherwise from
    // drawInternal into the bypass target.
    private boolean inMainPass() {
        return frameEntity || !IrisBypassTarget.isActive();
    }

    private void draw(RenderPass pass, boolean translucent) {
        if (failed) return;
        try {
            for (FrameDraw draw : frameDraws) {
                if (draw.translucent() != translucent) continue;
                if (frameEntity) {
                    draw.type().drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(entityVertices, null,
                            RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES).type(), 0, draw.first(),
                            draw.count(), PrimitiveTopology.TRIANGLES), pass);
                } else {
                    draw.type().drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(gpuVertices, gpuIndices, IndexType.INT,
                            0, draw.first(), draw.count(), PrimitiveTopology.TRIANGLES), pass);
                }
            }
        } catch (Exception exception) {
            failed = true;
            Mod.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
        }
    }

    // Same test AreaShape uses: whether Iris would widen an entity-format buffer built right now.
    private static boolean irisExtendsNow() {
        return IrisCompat.isPackInUse() && IrisCompat.isRenderingLevel() && !IrisCompat.skipExtension();
    }

    // The shadow pass matches a pipeline to a program by its vertex format and defines, so this is the entity
    // format with the alpha cutout entities use; Iris then draws it with the pack's shadow_entities program. The
    // shadow pass ignores the target a pass was made with, so the geometry lands in the shadow map either way.
    private static RenderPipeline shadowPipeline() {
        if (shadowEntityPipeline == null) {
            shadowEntityPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Mod.id("pipeline/obj_shadow"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());
            // Iris rejects a second assignment to the same pipeline, so this has to stay behind the null check.
            IrisCompat.assignPipelineShadow(shadowEntityPipeline, IrisCompat.ShadowProgram.ENTITIES);
        }
        return shadowEntityPipeline;
    }

    // The entity snippet's layout declares the lightmap and overlay samplers as well, and the vanilla entity path
    // binds all three; the shadow program only samples the albedo, but leaving the others unbound would make this
    // the one draw in the pass with a half-filled layout.
    private static RenderType shadowType(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(shadowPipeline())
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        return RenderType.create("vector3_obj_shadow", setup);
    }

    // Iris renders the shadow map before the main pass, so nothing of the main pass's frames can be reused there.
    // Without a pack there is no shadow pass (IrisCompat guards every call), and this never runs.
    static {
        if (IrisCompat.LOADED) {
            shadowPipeline();
            IrisCompat.registerShadowCallback(TexturedObjShape::submitShadowFrame);
        }
    }

    // Iris calls this from inside its shadow pass, after the opaque terrain. Only the tick delta is of use: the
    // pose is rebuilt from the shape's own state, and the pass's model view comes in as the base matrix.
    private static void submitShadowFrame(Matrix4f modelView, Matrix4f projection, double cameraX, double cameraY,
            double cameraZ, float tickDelta) {
        if (!IrisCompat.isPackInUse() || PreviewPass.isRendering()) return;
        try {
            List<TexturedObjShape> shapes = new ArrayList<>();
            int corners = 0;
            for (String shapeId : ShapeTrackRegistry.shapeIds()) {
                // Like the main pass, one broken shape only loses its own draws, never everyone's shadows.
                TexturedObjShape shape = null;
                try {
                    if (!(ShapeTrackRegistry.shape(shapeId) instanceof TexturedObjShape found)) continue;
                    shape = found;
                    if (shape.failed || !shape.enabled() || shape.tint().getAlpha() == 0 || shape.onScreenLayer()
                            || shape.model.corners.isEmpty()) continue;
                    // The shadow program binds the same entity mesh the main pass uses for entities; it has to be
                    // laid out for the widening Iris does to buffers built during a level render, which is the case
                    // here too.
                    if (shape.entityVertices == null || shape.entityExtended == null
                            || shape.entityExtended != irisExtendsNow()) shape.buildEntityMesh();
                    if (shape.entityVertices == null) continue;
                    // Every draw restarts at index 0 of the shared sequential buffer, so it only has to hold the
                    // largest mesh in the list; the meshes themselves are built before the pass opens.
                    corners = Math.max(corners, shape.model.corners.size());
                    shapes.add(shape);
                } catch (Exception exception) {
                    if (shape != null) {
                        shape.failed = true;
                        shape.shadowDraws.clear();
                    }
                    Mod.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
                }
            }
            if (shapes.isEmpty()) return;
            // The shadow pass runs before submitFeatures, so the frame-boundary resize has not happened yet: the
            // request is made and satisfied here, which is exactly what the main pass's reserve does.
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
            sequential.requestIndexCount(corners);
            sequential.resizeToRequestedIndexCount();
            Matrix4f base = new Matrix4f(modelView);
            for (TexturedObjShape shape : shapes) {
                try {
                    shape.prepareShadowDraws(base, tickDelta);
                } catch (Exception exception) {
                    shape.failed = true;
                    shape.shadowDraws.clear();
                    Mod.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
                }
            }
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "vector3_obj_shadow", target.getColorTextureView(), Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                for (TexturedObjShape shape : shapes) shape.drawShadow(pass);
            }
        } catch (Exception exception) {
            // Nothing may escape into Iris's shadow pass: a throw here would take the whole frame down.
            Mod.LOGGER.warn("OBJ shadow draw failed", exception);
        }
    }

    // The main pass's prepareDraws with the shadow pass's model view as the base. beforeDraw's camSpace flag asks
    // for the shape's position relative to the camera, exactly the space both model view matrices expect: the
    // vanilla entity path in this pass is posed the same way, inside the shadow model view.
    private void prepareShadowDraws(Matrix4f base, float tickDelta) {
        shadowDraws.clear();
        PoseStack poseStack = new PoseStack();
        beforeDraw(poseStack, tickDelta, true);
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        Matrix4f[] world = partMatrices(pose, false);
        ObjModel.Material chosen = mode == Mode.TEXTURE ? chosenMaterial() : ObjModel.Material.DEFAULT;
        if (world == null && mode != Mode.MATERIALS && noPartOverrides() && hiddenParts.isEmpty()) {
            addShadowDraw(new Matrix4f(base).mul(pose), chosen, 0, model.corners.size());
            return;
        }
        for (ObjModel.Range range : model.ranges) {
            String part = partName(range.part());
            if (part != null && hiddenParts.contains(part)) continue;
            Matrix4f partPose = world == null ? pose : new Matrix4f(pose).mul(world[range.part()]);
            ObjModel.Material material = mode == Mode.MATERIALS ? range.material() : chosen;
            addShadowDraw(new Matrix4f(base).mul(partPose), partMaterial(material, range.part()), range.first(),
                    range.count());
        }
    }

    // addDraw with the shadow pipeline: the shadow program reads no texture matrix, and the shadow map is never
    // sorted into a translucent and an opaque batch, so a draw is a draw.
    private void addShadowDraw(Matrix4f modelView, ObjModel.Material material, int first, int count) {
        float inverse = 1f / 255f;
        Color base = tint();
        Vector4f color = new Vector4f(base.getRed() * inverse * material.red(),
                base.getGreen() * inverse * material.green(), base.getBlue() * inverse * material.blue(),
                base.getAlpha() * inverse * material.alpha());
        if (color.w <= 0) return;
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(),
                new Matrix4f());
        PreparedRenderType prepared = shadowType(texture(material)).prepare();
        shadowDraws.add(new FrameDraw(new PreparedRenderType(prepared.name(), prepared.pipeline(),
                prepared.oitPipelineSet(), transform, prepared.scissorState(), prepared.textures()),
                first, count, false));
    }

    // The entity mesh is plain triangles in corner order, so it draws with the sequential index buffer, the way
    // the main pass draws the same mesh through Iris's entity program.
    private void drawShadow(RenderPass pass) {
        if (failed) return;
        try {
            for (FrameDraw draw : shadowDraws) {
                draw.type().drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(entityVertices, null,
                        RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES).type(), 0, draw.first(),
                        draw.count(), PrimitiveTopology.TRIANGLES), pass);
            }
        } catch (Exception exception) {
            failed = true;
            Mod.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
        }
    }

    // Vanilla's ENTITY_TRANSLUCENT, but triangles; Iris gives it its translucent entity program.
    private static RenderType entityType(Identifier texture, boolean seeThrough) {
        if (entityPipeline == null) {
            entityPipeline = entityPipeline("obj_entity", DepthStencilState.DEFAULT);
            entitySeeThroughPipeline = entityPipeline("obj_entity_see_through",
                    new DepthStencilState(CompareOp.ALWAYS_PASS, true));
        }
        RenderSetup setup = RenderSetup.builder(seeThrough ? entitySeeThroughPipeline : entityPipeline)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();
        return RenderType.create(seeThrough ? "vector3_obj_entity_see_through" : "vector3_obj_entity", setup);
    }

    private static RenderPipeline entityPipeline(String name, DepthStencilState depth) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Mod.id("pipeline/" + name))
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withDepthStencilState(depth)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build());
        IrisCompat.assignPipeline(pipeline, IrisCompat.Program.ENTITIES_TRANSLUCENT);
        return pipeline;
    }

    private @Nullable ObjModel.Material cachedChosen;

    // The texture picked in the editor, with its LabPBR companions (checked once).
    private ObjModel.Material chosenMaterial() {
        if (cachedChosen == null) {
            ObjModel.TextureRef texture = null;
            if (texturePath != null && !texturePath.isBlank()) {
                try {
                    Path path = Path.of(texturePath);
                    if (!path.isAbsolute()) path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
                    texture = new ObjModel.TextureRef(path.toAbsolutePath().normalize(), null);
                } catch (RuntimeException ignored) {
                    // Not a usable path; falls back to white.
                }
            }
            cachedChosen = ObjModel.Material.of(texture);
        }
        return cachedChosen;
    }

    // A resource texture with no explicit maps goes straight to the texture manager (Iris finds its _n / _s itself).
    // Anything else is decoded once into an ObjPbrTexture, which hands its maps to Iris. A texture that fails to
    // load is cached as white, so it is reported once rather than every frame.
    private static Identifier texture(ObjModel.Material material) {
        ObjModel.TextureRef ref = material.texture();
        if (ref == null) return white();
        if (ref.resource() != null && material.normal() == null && material.specular() == null) return ref.resource();
        TextureKey key = new TextureKey(ref, material.normal(), material.specular());
        Identifier cached = TEXTURES.get(key);
        if (cached != null) return cached;
        Identifier id;
        try {
            NativeImage image = ref.read();
            id = Mod.idFor("obj", key.toString());
            Minecraft.getInstance().getTextureManager().register(id,
                    new ObjPbrTexture(image, material.normal(), material.specular()));
        } catch (Exception exception) {
            Mod.LOGGER.warn("Could not load OBJ texture {}", ref, exception);
            id = white();
        }
        TEXTURES.put(key, id);
        return id;
    }

    private static Identifier white() {
        if (!whiteLoaded) {
            NativeImage image = new NativeImage(1, 1, false);
            image.setPixel(0, 0, 0xFFFFFFFF);
            Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "Vector3 OBJ white", image));
            whiteLoaded = true;
        }
        return WHITE;
    }

    public static void clearTextures() {
        Minecraft minecraft = Minecraft.getInstance();
        TEXTURES.values().stream().filter(id -> !id.equals(WHITE)).forEach(minecraft.getTextureManager()::release);
        TEXTURES.clear();
        if (whiteLoaded) minecraft.getTextureManager().release(WHITE);
        whiteLoaded = false;
    }

    @Override
    public void discard() {
        PREPARED.remove(this);
        frameDraws.clear();
        for (GpuBuffer buffer : new GpuBuffer[]{gpuVertices, gpuIndices, entityVertices}) {
            if (buffer != null) buffer.close();
        }
        gpuVertices = null;
        gpuIndices = null;
        entityVertices = null;
        super.discard();
    }
}
