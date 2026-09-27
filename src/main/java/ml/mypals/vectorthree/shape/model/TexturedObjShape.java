package ml.mypals.vectorthree.shape.model;

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
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.compat.IrisCompat;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import ml.mypals.vectorthree.render.ScreenLayer;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.shape.media.ImageDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final String DEFAULT_MODEL = "ryansrenderingkit:models/monkey.obj";
    private static final Identifier WHITE = Vector3.id("obj/white");
    private record TextureKey(ObjModel.TextureRef texture, @Nullable ObjModel.TextureRef normal,
            @Nullable ObjModel.TextureRef specular) {}

    private static final Map<TextureKey, Identifier> TEXTURES = new HashMap<>();
    private static final List<TexturedObjShape> PREPARED = new ArrayList<>();
    private static RenderPipeline entityPipeline, entitySeeThroughPipeline;
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
    private ObjModel model;
    private GpuBuffer gpuVertices;
    private GpuBuffer gpuIndices;
    private GpuBuffer entityVertices;
    private Boolean entityExtended;
    private boolean failed;
    private boolean frameEntity;
    private final List<FrameDraw> frameDraws = new ArrayList<>();

    public static Mode mode(ShapeState state) {
        return Mode.of(state.blockProperties() == null ? null : state.blockProperties().get(MODE));
    }

    public static @Nullable String texturePath(ShapeState state) {
        return state.blockProperties() == null ? null : state.blockProperties().get(TEXTURE);
    }

    public TexturedObjShape(String modelPath, Mode mode, @Nullable String texturePath, Color color, boolean seeThrough) {
        super(Shape.RenderingType.BATCH, color, seeThrough);
        this.transformer = new DefaultTransformer(this, Vec3.ZERO);
        this.transformFunction = transformer -> {};
        this.mode = mode;
        this.texturePath = texturePath;
        String source = modelPath == null || modelPath.isBlank() ? DEFAULT_MODEL : modelPath;
        try {
            model = ObjModel.load(source);
        } catch (Exception exception) {
            failed = true;
            model = new ObjModel();
            Vector3.LOGGER.warn("Could not load OBJ {}", source, exception);
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
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(n.x, n.y, n.z);
                }
            }
            MeshData mesh = builder.build();
            if (mesh == null) return;
            try {
                entityVertices = RenderSystem.getDevice().createBuffer(() -> "vector3_obj/entity_vertex",
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            } finally {
                mesh.close();
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
        if (failed || gpuVertices == null || !enabled() || baseColor.getAlpha() == 0 || onScreenLayer()) return;
        try {
            // Unless the shape bypasses shaders (ShapeState#bypassShaders), a shader pack shades it.
            frameEntity = IrisBypassTarget.isActive()
                    && !Boolean.TRUE.equals(customData.get(IrisBypassTarget.SHAPE_FLAG));
            if (frameEntity && (entityVertices == null || entityExtended != irisExtendsNow())) buildEntityMesh();
            if (frameEntity) {
                if (entityVertices == null) return;
                var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                sequential.requestIndexCount(model.corners.size());
                sequential.resizeToRequestedIndexCount();
            }
            prepareDraws(new Matrix4f(RenderSystem.getModelViewMatrixCopy()));
            PREPARED.add(this);
        } catch (Exception exception) {
            failed = true;
            frameDraws.clear();
            Vector3.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
        }
    }

    private void prepareDraws(Matrix4f base) {
        PoseStack poseStack = new PoseStack();
        beforeDraw(poseStack, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
        Matrix4f modelView = base.mul(poseStack.last().pose());
        if (mode == Mode.MATERIALS) {
            for (ObjModel.Range range : model.ranges) addDraw(modelView, range.material(), range.first(), range.count());
        } else {
            addDraw(modelView, mode == Mode.TEXTURE ? chosenMaterial() : ObjModel.Material.DEFAULT, 0,
                    model.corners.size());
        }
    }

    private boolean onScreenLayer() {
        return Boolean.TRUE.equals(customData.get(ScreenLayer.SHAPE_FLAG));
    }

    // On the UI layer the mesh skips the level's main pass and is drawn here, with the UI pass's ortho projection.
    private void drawOnScreen() {
        frameDraws.clear();
        if (failed || gpuVertices == null || baseColor.getAlpha() == 0) return;
        try {
            frameEntity = false;
            prepareDraws(ScreenLayer.basePose());
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
            Vector3.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
        } finally {
            frameDraws.clear();
        }
    }

    private void addDraw(Matrix4f modelView, ObjModel.Material material, int first, int count) {
        float inverse = 1f / 255f;
        Vector4f color = new Vector4f(baseColor.getRed() * inverse * material.red(),
                baseColor.getGreen() * inverse * material.green(), baseColor.getBlue() * inverse * material.blue(),
                baseColor.getAlpha() * inverse * material.alpha());
        if (color.w <= 0) return;
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(),
                new Matrix4f());
        Identifier texture = texture(material);
        PreparedRenderType original = (frameEntity ? entityType(texture, seeThrough)
                : ShapeTrackRegistry.objType(texture, seeThrough)).prepare();
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
            Vector3.LOGGER.warn("OBJ draw failed, pausing it until the shape is rebuilt", exception);
        }
    }

    // Same test AreaShape uses: whether Iris would widen an entity-format buffer built right now.
    private static boolean irisExtendsNow() {
        return IrisCompat.isPackInUse() && IrisCompat.isRenderingLevel() && !IrisCompat.skipExtension();
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
                .withLocation(Vector3.id("pipeline/" + name))
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
            id = Vector3.id("obj/" + Integer.toUnsignedString(key.hashCode(), 36));
            Minecraft.getInstance().getTextureManager().register(id,
                    new ObjPbrTexture(image, material.normal(), material.specular()));
        } catch (Exception exception) {
            Vector3.LOGGER.warn("Could not load OBJ texture {}", ref, exception);
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
