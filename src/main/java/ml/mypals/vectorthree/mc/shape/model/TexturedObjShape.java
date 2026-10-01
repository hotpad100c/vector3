package ml.mypals.vectorthree.mc.shape.model;

import ml.mypals.vectorthree.mc.render.PreparedDraws;
import ml.mypals.vectorthree.core.Mod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.tags.EmptyMesh;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.transform.shapeTransformers.DefaultTransformer;
import ml.mypals.vectorthree.core.pose.EntityPose;
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
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final String DEFAULT_MODEL = "ryansrenderingkit:models/monkey.obj";
    private static final Identifier WHITE = Mod.id("obj/white");
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
    private final boolean lighting;
    private final String shapeId;
    private Map<String, Vector3f> pivots;
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

    public void setPivots(Map<String, Vector3f> pivots) {
        this.pivots = pivots;
    }

    public List<String> partNames() {
        return model.parts.stream().map(ObjModel.Part::name).toList();
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
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(n.x, n.y, n.z);
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
        if (failed || gpuVertices == null || !enabled() || baseColor.getAlpha() == 0 || onScreenLayer()) return;
        try {
            // Unless the shape bypasses shaders (ShapeState#bypassShaders), a shader pack shades it.
            frameEntity = IrisBypassTarget.isActive()
                    && !Boolean.TRUE.equals(customData.get(IrisBypassTarget.SHAPE_FLAG));
            if (frameEntity && (entityVertices == null || entityExtended != irisExtendsNow())) buildEntityMesh();
            if (frameEntity) {
                if (entityVertices == null) return;
                PreparedDraws.reserveSequential(PrimitiveTopology.TRIANGLES, model.corners.size());
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
        if (world == null && mode != Mode.MATERIALS) {
            addDraw(new Matrix4f(base).mul(pose), pose, chosen, 0, model.corners.size());
            return;
        }
        for (ObjModel.Range range : model.ranges) {
            Matrix4f partPose = world == null ? pose : new Matrix4f(pose).mul(world[range.part()]);
            addDraw(new Matrix4f(base).mul(partPose), partPose, mode == Mode.MATERIALS ? range.material() : chosen,
                    range.first(), range.count());
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
        if (failed || gpuVertices == null || baseColor.getAlpha() == 0) return;
        try {
            frameEntity = false;
            prepareDraws(ScreenLayer.basePose(), false);
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "vector3_obj_screen",
                    target.getColorTextureView(), Optional.empty(), target.useDepth ? target.getDepthTextureView() : null,
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
    // used while a shader pack is loaded, even when the shape bypasses it: the pack does the lighting then.
    private void addDraw(Matrix4f modelView, Matrix4f pose, ObjModel.Material material, int first, int count) {
        float inverse = 1f / 255f;
        Vector4f color = new Vector4f(baseColor.getRed() * inverse * material.red(),
                baseColor.getGreen() * inverse * material.green(), baseColor.getBlue() * inverse * material.blue(),
                baseColor.getAlpha() * inverse * material.alpha());
        if (color.w <= 0) return;
        boolean lit = lighting && !frameEntity && !IrisCompat.isPackInUse();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(modelView, color, new Vector3f(),
                lit ? pose : new Matrix4f());
        Identifier texture = texture(material);
        PreparedRenderType original = (frameEntity ? entityType(texture, seeThrough)
                : ShapeTrackRegistry.objType(texture, seeThrough, lit)).prepare();
        frameDraws.add(new FrameDraw(PreparedDraws.withTransform(original, transform),
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
        if (!hasMainPassDraws(true)) return;
        try (RenderPass pass = mainPass("vector3_obj")) {
            drawPreparedTranslucent(pass);
        }
    }

    public static void drawPreparedOpaque() {
        if (!hasMainPassDraws(false)) return;
        try (RenderPass pass = mainPass("vector3_obj_opaque")) {
            drawPreparedOpaque(pass);
        }
    }

    private static boolean hasMainPassDraws(boolean translucent) {
        return PREPARED.stream().anyMatch(shape -> shape.inMainPass()
                && shape.frameDraws.stream().anyMatch(draw -> draw.translucent() == translucent));
    }

    private static RenderPass mainPass(String label) {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label,
                target.getColorTextureView(), Optional.empty(), target.useDepth ? target.getDepthTextureView() : null,
                OptionalDouble.empty());
        RenderSystem.bindDefaultUniforms(pass);
        return pass;
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
                    PreparedDraws.draw(pass, draw.type(), entityVertices, null, null, 0, draw.first(),
                            draw.count(), PrimitiveTopology.TRIANGLES);
                } else {
                    PreparedDraws.draw(pass, draw.type(), gpuVertices, gpuIndices, IndexType.INT,
                            0, draw.first(), draw.count(), PrimitiveTopology.TRIANGLES);
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
