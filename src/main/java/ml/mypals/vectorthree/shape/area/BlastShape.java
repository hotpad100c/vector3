package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import ml.mypals.vectorthree.compat.IrisCompat;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.line.StripLineShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.blast.BlastMotion;
import ml.mypals.vectorthree.shape.blast.BlastSettings;
import ml.mypals.vectorthree.shape.point.ShapePoint;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An area whose blocks fly apart to a scatter cloud, or gather from it, along a trajectory. The geometry is uploaded
 * once (BlastGpu); each frame picks a way to move it:
 * <ul>
 *   <li>CLUMPS: one draw per clump with its own transform, faces inside clumps left out (few clumps; works with
 *       shader packs, which only see vanilla pipelines);</li>
 *   <li>SKINNED: one draw, every block moved in core/blast_skin (many clumps, no shader pack);</li>
 *   <li>CPU: the vertices moved on the CPU into reused buffers (many clumps under a shader pack).</li>
 * </ul>
 */
public final class BlastShape extends AreaShape {
    private static final Color PATH_COLOR = new Color(255, 170, 60, 220);
    private static final Color SWEEP_COLOR = new Color(255, 80, 80, 220);

    // Set from AreaShape's constructor (via bakeContent/updateTransform), so they must not have initializers.
    private List<BlastBaker.Piece> pieces;
    private BlastMotion motion;
    private boolean truncated;
    private BlastSettings settings;
    private List<Vector3f> path;
    private Vector3f pathStart;
    private Vector3f origin;
    private Vector3f sweepEnd;
    private Vector3f scatter;
    private StripLineShape sweepPreview;
    private boolean screen;
    private ByteBufferBuilder[] buffers;
    private float[] pack;
    private int[] levels;
    private boolean translucentFrame;
    private Object builtKey;
    private enum Mode { CLUMPS, SKINNED, CPU }
    private static final int MAX_CLUMP_DRAWS = 1024;
    // Set once core/blast_skin failed to build; the CPU path takes over from then on.
    private static boolean skinUnavailable;
    private Mode mode;
    private BlastGpu gpu;
    private Object gpuKey;
    private int[] order;
    // Runs of consecutive blocks (in clump order) that move together: start position, end position, level.
    private int[] groupStart, groupEnd, groupLevel;
    private int groupCount;
    private GpuBuffer transformBuffer;
    private final List<ClumpDraw> clumpDraws = new ArrayList<>();
    private PreparedRenderType[] skinnedTypes;

    private record ClumpDraw(int layer, PreparedRenderType type, GpuBuffer indices, int first, int count) {}
    private StripLineShape pathPreview;

    public BlastShape(ShapeState state, Color color, boolean seeThrough) {
        super(state, color, seeThrough);
    }

    public boolean truncated() {
        return truncated;
    }

    @Override
    public void updateTransform(ShapeState state) {
        super.updateTransform(state);
        settings = BlastSettings.orDefault(state.blast());
        screen = state.screen();
        List<ShapePoint> all = state.points() == null ? List.of() : state.points();
        pathStart = local(all, PATH_START);
        origin = local(all, ORIGIN);
        sweepEnd = local(all, SWEEP_END);
        scatter = local(all, SCATTER);
        List<Vector3f> points = new ArrayList<>();
        for (int i = FIRST_PATH_POINT; i < all.size(); i++) points.add(local(all, i));
        path = points;
    }

    /** Points 0 and 1 are the source corners (world); the rest are local: path start, origin, then the path. */
    public static final int PATH_START = 2, ORIGIN = 3, SWEEP_END = 4, SCATTER = 5, FIRST_PATH_POINT = 6;

    private static final Color CORNER_COLOR = new Color(60, 140, 255, 240);
    private static final Color ORIGIN_COLOR = new Color(60, 230, 170, 240);
    private static final Color SCATTER_COLOR = new Color(220, 90, 255, 240);

    /** What point {@code index} is for under the current options (a lang key), or null when nothing uses it. */
    public static @org.jetbrains.annotations.Nullable String pointRole(ShapeState state, int index) {
        BlastSettings s = BlastSettings.orDefault(state.blast());
        boolean path = s.trajectory() == BlastSettings.Trajectory.PATH;
        if (index < PATH_START) return "vector3.blast.point.corner";
        // On the UI layer the local points live in screen space, while the geometry gizmo works in the world.
        if (state.screen()) return null;
        return switch (index) {
            case PATH_START -> path ? "vector3.blast.path_start" : null;
            case ORIGIN -> s.order() == BlastSettings.Order.SWEEP ? "vector3.blast.sweep_start"
                    : s.order() == BlastSettings.Order.FROM_ORIGIN || s.trajectory() == BlastSettings.Trajectory.EXPLODE
                    ? "vector3.blast.origin" : null;
            case SWEEP_END -> s.order() == BlastSettings.Order.SWEEP ? "vector3.blast.sweep_end" : null;
            case SCATTER -> "vector3.blast.scatter_center";
            default -> path ? "vector3.blast.path_point" : null;
        };
    }

    public static Color pointColor(String role) {
        return switch (role) {
            case "vector3.blast.point.corner" -> CORNER_COLOR;
            case "vector3.blast.path_start", "vector3.blast.path_point" -> PATH_COLOR;
            case "vector3.blast.sweep_start", "vector3.blast.sweep_end" -> SWEEP_COLOR;
            case "vector3.blast.scatter_center" -> SCATTER_COLOR;
            default -> ORIGIN_COLOR;
        };
    }

    public static String pointLabel(ShapeState state, int index) {
        String role = pointRole(state, index);
        if (role == null) return "";
        return role.equals("vector3.blast.path_point")
                ? net.minecraft.client.resources.language.I18n.get(role, index - FIRST_PATH_POINT + 1)
                : net.minecraft.client.resources.language.I18n.get(role);
    }

    // Which points the geometry gizmo shows; it rebuilds when this changes.
    public static String pointLayout(ShapeState state) {
        StringBuilder layout = new StringBuilder();
        int count = state.points() == null ? 0 : state.points().size();
        for (int i = 0; i < count; i++) layout.append(pointRole(state, i) == null ? '-' : '+');
        return layout.toString();
    }

    private static Vector3f local(List<ShapePoint> points, int index) {
        if (index >= points.size()) return new Vector3f();
        ShapePoint point = points.get(index);
        return new Vector3f((float) point.x(), (float) point.y(), (float) point.z());
    }

    @Override
    protected Matrix4d meshToSource() {
        Vec3 center = sourceCenter();
        return new Matrix4d().translation(center.x, center.y, center.z);
    }

    @Override
    protected void bakeContent(ClientLevel level, BlockPos min, BlockPos max) {
        BlastBaker.Result result = new BlastBaker().bake(level, min, max);
        pieces = result.pieces();
        truncated = result.truncated();
        blockCount = pieces.size();
        Vec3 center = sourceCenter();
        int count = pieces.size();
        int[] gx = new int[count], gy = new int[count], gz = new int[count];
        Vector3f[] centers = new Vector3f[count];
        pack = new float[count * BlastMotion.PACK];
        levels = new int[count];
        for (int i = 0; i < count; i++) {
            BlockPos pos = pieces.get(i).pos();
            gx[i] = pos.getX() - min.getX();
            gy[i] = pos.getY() - min.getY();
            gz[i] = pos.getZ() - min.getZ();
            centers[i] = new Vector3f((float) (pos.getX() + 0.5 - center.x), (float) (pos.getY() + 0.5 - center.y),
                    (float) (pos.getZ() + 0.5 - center.z));
        }
        motion = new BlastMotion(gx, gy, gz, centers);
        builtKey = null;
        closeGpu();
        List<BlockEntity> withEntities = new ArrayList<>();
        for (BlastBaker.Piece piece : pieces) if (piece.blockEntity() != null) withEntities.add(piece.blockEntity());
        blockEntities = withEntities;
    }

    private void closeGpu() {
        if (gpu != null) gpu.close();
        gpu = null;
        gpuKey = null;
    }

    @Override
    protected boolean forceTranslucent() {
        return translucentFrame;
    }

    @Override
    protected void beforePrepare() {
        if (pieces == null || motion == null) return;
        float progress = BlastMotion.progress(settings);
        boolean extended = irisExtendsNow();
        Object key = List.of(progress, settings, path, pathStart, origin, sweepEnd, scatter, extended);
        if (key.equals(builtKey)) return;
        builtKey = key;
        motion.evaluate(settings, pathStart, path, origin, sweepEnd, scatter, progress, pack, levels);
        order = motion.order(settings);
        groupClumps();
        boolean anyTranslucent = false;
        for (int i = 0; i < pieces.size(); i++) if (pack[i * BlastMotion.PACK + 11] < 0.999f) anyTranslucent = true;
        translucentFrame = anyTranslucent;
        mode = groupCount <= MAX_CLUMP_DRAWS ? Mode.CLUMPS
                : IrisCompat.isPackInUse() || skinUnavailable ? Mode.CPU : Mode.SKINNED;
        if (mode == Mode.CPU) {
            buildCpuMeshes();
            return;
        }
        Object wantedGpu = List.of(settings.seed(), settings.clumpShape(), settings.clumpJitter(), extended);
        if (gpu == null || !wantedGpu.equals(gpuKey)) {
            closeGpu();
            gpu = new BlastGpu(pieces, motion, order, sourceCenter(), extended);
            gpuKey = wantedGpu;
        }
        if (mode == Mode.SKINNED) uploadTransforms();
    }

    // Consecutive blocks in clump order that sit in the same clump at their current level.
    private void groupClumps() {
        int count = order.length;
        if (groupStart == null || groupStart.length < count) {
            groupStart = new int[count];
            groupEnd = new int[count];
            groupLevel = new int[count];
        }
        groupCount = 0;
        for (int position = 0; position < count; position++) {
            int block = order[position];
            int level = levels[block];
            if (groupCount > 0) {
                int previous = order[position - 1];
                int last = groupCount - 1;
                if (groupLevel[last] == level && motion.clumpKey(previous, level) == motion.clumpKey(block, level)) {
                    groupEnd[last] = position + 1;
                    continue;
                }
            }
            groupStart[groupCount] = position;
            groupEnd[groupCount] = position + 1;
            groupLevel[groupCount] = level;
            groupCount++;
        }
    }

    private void uploadTransforms() {
        int bytes = pack.length * 4;
        if (transformBuffer == null || transformBuffer.size() < bytes) {
            if (transformBuffer != null) transformBuffer.close();
            transformBuffer = RenderSystem.getDevice().createBuffer(() -> "vector3_blast/transforms",
                    GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, Math.max(bytes, 48));
        }
        ByteBuffer data = MemoryUtil.memAlloc(bytes);
        try {
            data.asFloatBuffer().put(pack);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(transformBuffer.slice(0, bytes), data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    // Faces a neighbor in the same clump would hide are left out here too.
    private void buildCpuMeshes() {
        if (buffers == null) buffers = new ByteBufferBuilder[]{new ByteBufferBuilder(1 << 16), new ByteBufferBuilder(1 << 12), new ByteBufferBuilder(1 << 12)};
        BufferBuilder solid = builder(buffers[0], AreaRenderType.getSolid());
        BufferBuilder cutout = builder(buffers[1], AreaRenderType.getCutout());
        BufferBuilder translucent = builder(buffers[2], AreaRenderType.get());
        Vec3 center = sourceCenter();
        Vector3f position = new Vector3f(), normal = new Vector3f();
        Matrix4f transform = new Matrix4f();
        Matrix3f normalMatrix = new Matrix3f();
        for (int i = 0; i < pieces.size(); i++) {
            float alpha = pack[i * BlastMotion.PACK + 11];
            if (alpha <= 0.001f) continue;
            BlastBaker.Piece piece = pieces.get(i);
            BlastMotion.matrix(pack, i, transform);
            normalMatrix.set(transform).normal();
            float ox = (float) (piece.pos().getX() - center.x), oy = (float) (piece.pos().getY() - center.y),
                    oz = (float) (piece.pos().getZ() - center.z);
            write(solid, piece.solid(), i, transform, normalMatrix, ox, oy, oz, alpha, position, normal);
            write(cutout, piece.cutout(), i, transform, normalMatrix, ox, oy, oz, alpha, position, normal);
            write(translucent, piece.translucent(), i, transform, normalMatrix, ox, oy, oz, alpha, position, normal);
        }
        solidMesh.uploadReusing(solid.build());
        cutoutMesh.uploadReusing(cutout.build());
        translucentMesh.upload(translucent.build());
    }

    private static BufferBuilder builder(ByteBufferBuilder bytes, RenderType type) {
        return new BufferBuilder(bytes, type.primitiveTopology(), type.format());
    }

    private void write(BufferBuilder builder, BlastBaker.Layer layer, int block, Matrix4f transform, Matrix3f normalMatrix,
            float ox, float oy, float oz, float alpha, Vector3f position, Vector3f normal) {
        int quads = layer.quadCount();
        if (quads == 0) return;
        float[] data = layer.data.elements();
        int[] colors = layer.colors.elements();
        int[] lights = layer.lights.elements();
        int[] occluders = layer.occluders.elements();
        int level = levels[block];
        for (int quad = 0; quad < quads; quad++) {
            int occluder = occluders[quad];
            if (occluder >= 0 && motion.sharedLevel(block, occluder) <= level) continue;
            for (int v = quad * 4; v < quad * 4 + 4; v++) {
                int at = v * 8;
                transform.transformPosition(position.set(data[at] + ox, data[at + 1] + oy, data[at + 2] + oz));
                normalMatrix.transform(normal.set(data[at + 5], data[at + 6], data[at + 7]));
                if (normal.lengthSquared() > 1.0e-8f) normal.normalize();
                int color = colors[v];
                if (alpha < 0.999f) color = Math.round(((color >>> 24) & 255) * alpha) << 24 | color & 0xFFFFFF;
                builder.addVertex(position.x, position.y, position.z, color, data[at + 3], data[at + 4],
                        OverlayTexture.NO_OVERLAY, lights[v], normal.x, normal.y, normal.z);
            }
        }
    }

    // Each block entity rides its block: same transform, same alpha.
    @Override
    protected void submitBlockEntities(SubmitNodeCollector collector, CameraRenderState camera, Matrix4f model) {
        if (pack == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float baseAlpha = baseColor.getAlpha() / 255f;
        Vec3 viewer = sourceViewer(camera), center = sourceCenter();
        Matrix4f block = new Matrix4f();
        AreaSuppression.bypassing(() -> {
            for (int i = 0; i < pieces.size(); i++) {
                BlastBaker.Piece piece = pieces.get(i);
                if (piece.blockEntity() == null) continue;
                float alpha = baseAlpha * pack[i * BlastMotion.PACK + 11];
                if (alpha <= 0.001f) continue;
                BlockEntityRenderState state = extractBlockEntity(piece.blockEntity(), partialTick, viewer);
                if (state == null) continue;
                Matrix4f placed = new Matrix4f(model).mul(BlastMotion.matrix(pack, i, block));
                Vector3f at = placed.transformPosition(new Vector3f((float) (piece.pos().getX() - center.x),
                        (float) (piece.pos().getY() - center.y), (float) (piece.pos().getZ() - center.z)));
                PoseStack poseStack = new PoseStack();
                poseStack.translate(at.x, at.y, at.z);
                poseStack.mulPose(placed.setTranslation(0, 0, 0));
                dispatcher.submit(state, poseStack, alpha < 1 ? new AreaTintedCollector(collector, alpha) : collector, camera);
            }
        });
    }

    @Override
    protected boolean hasContent() {
        if (mode == null || mode == Mode.CPU) return super.hasContent();
        return gpu != null && !gpu.isEmpty();
    }

    @Override
    protected void prepareMesh(Matrix4f model, Matrix4f fogModel, boolean ownShader) {
        if (mode == null || mode == Mode.CPU) {
            super.prepareMesh(model, fogModel, ownShader);
            return;
        }
        Vector4f base = colorToVector4f(baseColor);
        frameTranslucent = base.w() < 1 || translucentFrame;
        Matrix4f view = RenderSystem.getModelViewMatrixCopy();
        clumpDraws.clear();
        if (mode == Mode.SKINNED) {
            GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(view).mul(model),
                    base, new Vector3f(), fogModel);
            skinnedTypes = new PreparedRenderType[BlastGpu.LAYERS];
            for (int layer = 0; layer < BlastGpu.LAYERS; layer++) {
                RenderType type = AreaRenderType.skinned(frameTranslucent ? 2 : layer);
                skinnedTypes[layer] = withTransform(type, transform);
            }
            return;
        }
        Matrix4f clump = new Matrix4f();
        for (int group = 0; group < groupCount; group++) {
            int representative = order[groupStart[group]];
            float alpha = pack[representative * BlastMotion.PACK + 11];
            if (alpha <= 0.001f) continue;
            BlastMotion.matrix(pack, representative, clump);
            Matrix4f placed = new Matrix4f(model).mul(clump);
            GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(view).mul(placed),
                    new Vector4f(base.x(), base.y(), base.z(), base.w() * alpha), new Vector3f(),
                    ownShader ? new Matrix4f(fogModel).mul(clump) : new Matrix4f());
            int level = groupLevel[group];
            for (int layer = 0; layer < BlastGpu.LAYERS; layer++) {
                int[] start = gpu.starts[level][layer];
                int first = start[groupStart[group]], count = start[groupEnd[group]] - first;
                if (count <= 0 || gpu.indices[level][layer] == null) continue;
                RenderType type = frameTranslucent || layer == 2 ? AreaRenderType.get(ownShader)
                        : layer == 0 ? AreaRenderType.getSolid(ownShader) : AreaRenderType.getCutout(ownShader);
                clumpDraws.add(new ClumpDraw(layer, withTransform(type, transform), gpu.indices[level][layer], first, count));
            }
        }
    }

    @Override
    protected void drawOpaqueMeshes(RenderPass pass) {
        if (mode == null || mode == Mode.CPU) {
            super.drawOpaqueMeshes(pass);
            return;
        }
        draw(pass, false);
    }

    @Override
    protected void drawTranslucentMesh(RenderPass pass) {
        if (mode == null || mode == Mode.CPU) {
            super.drawTranslucentMesh(pass);
            return;
        }
        draw(pass, true);
    }

    private void draw(RenderPass pass, boolean translucentLayer) {
        if (gpu == null) return;
        if (mode == Mode.SKINNED) {
            for (int layer = 0; layer < BlastGpu.LAYERS; layer++) {
                if ((layer == 2) == translucentLayer) drawSkinned(pass, layer);
            }
            return;
        }
        for (ClumpDraw draw : clumpDraws) {
            if ((draw.layer() == 2) != translucentLayer) continue;
            GpuBuffer vertices = gpu.vertices[draw.layer()];
            if (vertices == null) continue;
            draw.type().drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(vertices, draw.indices(), IndexType.INT, 0,
                    draw.first(), draw.count(), draw.type().pipeline().getPrimitiveTopology()), pass);
        }
    }

    // PreparedRenderType#drawFromBuffer, plus the two texel buffers core/blast_skin reads.
    private void drawSkinned(RenderPass pass, int layer) {
        GpuBuffer vertices = gpu.vertices[layer], indices = gpu.indices[0][layer];
        int count = gpu.starts[0][layer][order.length];
        if (vertices == null || indices == null || count == 0 || transformBuffer == null || skinnedTypes == null) return;
        PreparedRenderType type = skinnedTypes[layer];
        try {
            pass.setPipeline(RenderSystem.getCompiledPipeline(type.pipeline()));
        } catch (IllegalStateException exception) {
            if (!skinUnavailable) Vector3.LOGGER.warn("GPU-skinned blast pipeline unavailable, moving blocks on the CPU", exception);
            skinUnavailable = true;
            builtKey = null;
            return;
        }
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", type.dynamicTransforms());
        pass.setVertexBuffer(0, vertices.slice());
        for (PreparedRenderType.Texture texture : type.textures()) {
            pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
        }
        pass.setUniform("BlastBlockOf", gpu.blockOf[layer]);
        pass.setUniform("BlastTransforms", transformBuffer);
        pass.setIndexBuffer(indices, IndexType.INT);
        pass.drawIndexed(count, 1, 0, 0, 0);
    }

    // Only while this shape is selected in the editor.
    @Override
    protected void editorFrame() {
        if (settings == null || motion == null) return;
        updatePathPreview();
        updateSweepPreview(BlastMotion.progress(settings));
    }

    // Editor-only: the custom path through its points to the scatter center.
    private void updatePathPreview() {
        if (settings.trajectory() != BlastSettings.Trajectory.PATH || !editorOverlays()) {
            removePathPreview();
            return;
        }
        Matrix4d toWorld = destTransform().mul(meshToSource());
        List<Vec3> vertices = new ArrayList<>();
        vertices.add(world(toWorld, pathStart));
        for (Vector3f point : path) vertices.add(world(toWorld, point));
        vertices.add(world(toWorld, scatter));
        if (pathPreview == null) {
            pathPreview = (StripLineShape) ShapeGenerator.generateStripLine().vertexes(vertices).lineWidth(2f)
                    .color(PATH_COLOR).seeThrough(true).build(Shape.RenderingType.BATCH);
            ShapeManagers.addShape(pathPreviewId(), pathPreview);
        } else {
            pathPreview.setVertexes(vertices);
        }
    }

    private boolean editorOverlays() {
        return !screen && ReplayUI.isActive() && !Flashback.isExporting()
                && shapeId.equals(Vector3.GIZMO_EDITOR.selectedShapeId());
    }

    // Editor-only: the sweep volume where it is now, and its line.
    private void updateSweepPreview(float progress) {
        if (settings.order() != BlastSettings.Order.SWEEP || !editorOverlays()) {
            removeSweepPreview();
            return;
        }
        Matrix4d toWorld = destTransform().mul(meshToSource());
        float u = BlastMotion.sweepPosition(settings, progress);
        Vector3f at = new Vector3f(origin).lerp(sweepEnd, u);
        float r = settings.sweepRadius();
        List<Vec3> vertices = new ArrayList<>();
        vertices.add(world(toWorld, origin));
        vertices.add(world(toWorld, sweepEnd));
        vertices.add(world(toWorld, at));
        if (settings.sweepShape() == BlastSettings.SweepShape.CUBE) {
            float[][] corners = {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}, {-1, -1, -1}, {-1, 1, -1},
                    {1, 1, -1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}, {-1, -1, 1},
                    {-1, 1, 1}, {-1, 1, -1}};
            for (float[] c : corners) vertices.add(world(toWorld, new Vector3f(c[0], c[1], c[2]).mul(r).add(at)));
        } else {
            for (int plane = 0; plane < 3; plane++) {
                for (int i = 0; i <= 32; i++) {
                    double angle = Math.PI * 2 * i / 32;
                    float c = (float) Math.cos(angle) * r, s = (float) Math.sin(angle) * r;
                    Vector3f point = plane == 0 ? new Vector3f(c, s, 0) : plane == 1 ? new Vector3f(c, 0, s) : new Vector3f(0, c, s);
                    vertices.add(world(toWorld, point.add(at)));
                }
            }
        }
        if (sweepPreview == null) {
            sweepPreview = (StripLineShape) ShapeGenerator.generateStripLine().vertexes(vertices).lineWidth(2f)
                    .color(SWEEP_COLOR).seeThrough(true).build(Shape.RenderingType.BATCH);
            ShapeManagers.addShape(sweepPreviewId(), sweepPreview);
        } else {
            sweepPreview.setVertexes(vertices);
        }
    }

    private Identifier sweepPreviewId() {
        return Vector3.id("gizmo_blast_sweep/" + Integer.toHexString(Objects.hashCode(shapeId)));
    }

    private void removeSweepPreview() {
        if (sweepPreview == null) return;
        sweepPreview.discard();
        ShapeManagers.removeShapes(sweepPreviewId());
        sweepPreview = null;
    }

    private static Vec3 world(Matrix4d toWorld, Vector3f local) {
        Vector3d world = toWorld.transformPosition(new Vector3d(local.x, local.y, local.z));
        return new Vec3(world.x, world.y, world.z);
    }

    private Identifier pathPreviewId() {
        return Vector3.id("gizmo_blast_path/" + Integer.toHexString(Objects.hashCode(shapeId)));
    }

    private void removePathPreview() {
        if (pathPreview == null) return;
        pathPreview.discard();
        ShapeManagers.removeShapes(pathPreviewId());
        pathPreview = null;
    }

    @Override
    public void discard() {
        removePathPreview();
        removeSweepPreview();
        if (buffers != null) for (ByteBufferBuilder buffer : buffers) buffer.close();
        buffers = null;
        closeGpu();
        if (transformBuffer != null) transformBuffer.close();
        transformBuffer = null;
        super.discard();
    }
}
