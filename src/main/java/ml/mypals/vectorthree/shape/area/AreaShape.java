package ml.mypals.vectorthree.shape.area;

import ml.mypals.vectorthree.compat.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.tags.EmptyMesh;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.render.ScreenLayer;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.shape.point.ShapePoint;
import ml.mypals.vectorthree.shape.ShapeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;


public class AreaShape extends Shape implements EmptyMesh {
    protected final AreaGpuMesh solidMesh = new AreaGpuMesh();
    protected final AreaGpuMesh cutoutMesh = new AreaGpuMesh();
    protected final AreaTranslucentGpuMesh translucentMesh = new AreaTranslucentGpuMesh();
    // CPU-side, not a GPU mesh — see AreaBaker.Result's javadoc for why.
    private List<AreaBaker.OutlineVertex> outlineVertices = List.of();
    protected final String shapeId;
    protected int blockCount;
    private boolean outlineEnabled;
    private Vector4f outlineColor = new Vector4f(1, 1, 1, 1);
    private Vec3 localMin = new Vec3(-0.5, -0.5, -0.5);
    private Vec3 localMax = new Vec3(0.5, 0.5, 0.5);
    private Vec3 sourceCenter = Vec3.ZERO;
    private final Matrix4d localTransform = new Matrix4d();
    private final Matrix4d parentTransform = new Matrix4d();
    // Source-region world coordinates to destination world coordinates.
    private final Matrix4d destTransform = new Matrix4d();
    private List<BlockEntity> blockEntities = List.of();
    private AABB sourceBounds;
    private AreaOptions options = AreaOptions.DEFAULT;
    private boolean projectionShown;
    private float projectionAlpha = 1.0f;
    private AreaProjection.Projection projection;
    // On the UI layer only the baked copy is drawn, flat on the screen; the source region stays in the world.
    private boolean screen;

    public AreaShape(ShapeState state, Color color, boolean seeThrough) {
        super(RenderingType.BATCH, transformer -> {}, color, Vec3.ZERO, seeThrough);
        this.shapeId = state.shapeId();
        updateTransform(state);
        bake(state);
        generateRawGeometry(false);
    }

    public int blockCount() {
        return blockCount;
    }

    public void updateTransform(ShapeState state) {
        localTransform.identity().translate(state.x(), state.y(), state.z())
                .rotateXYZ(Math.toRadians(state.pitch()), Math.toRadians(state.yaw()), Math.toRadians(state.roll()))
                .scale(state.scaleX(), state.scaleY(), state.scaleZ());
        updateDestTransform();
        outlineEnabled = state.outline();
        outlineColor = colorToVector4f(new Color(state.outlineColor(), true));
        options = AreaOptions.orDefault(state.areaOptions());
        screen = state.screen();
        projectionShown = state.visible() && ((state.color() >>> 24) & 0xFF) > 0;
        projectionAlpha = ((state.color() >>> 24) & 0xFF) / 255.0f;
        publishProjection();
    }

    public void setParentTransform(Matrix4fc parent) {
        Matrix4d next = new Matrix4d(parent);
        if (next.equals(parentTransform)) return;
        parentTransform.set(next);
        updateDestTransform();
        publishProjection();
    }

    protected Vec3 sourceCenter() {
        return sourceCenter;
    }

    /** Source-region world coordinates to destination world coordinates. */
    protected Matrix4d destTransform() {
        return new Matrix4d(destTransform);
    }

    /** What the meshes' coordinates are relative to, in source-region world coordinates. */
    protected Matrix4d meshToSource() {
        return new Matrix4d();
    }

    private void updateDestTransform() {
        destTransform.set(parentTransform).mul(localTransform).translate(-sourceCenter.x, -sourceCenter.y, -sourceCenter.z);
    }

    /** Tells the entity/particle mixins where this area carries its source region's contents. */
    private void publishProjection() {
        boolean wanted = !screen && sourceBounds != null && projectionShown
                && (options.projectEntities() || options.projectParticles());
        projection = wanted ? new AreaProjection.Projection(sourceBounds, new Matrix4d(destTransform),
                options.projectEntities(), options.projectParticles(), shapeId, projectionAlpha) : null;
        AreaProjection.set(shapeId, projection);
    }

    private BlockPos bakedMin = BlockPos.ZERO;
    private BlockPos bakedMax = BlockPos.ZERO;

    private void bake(ShapeState state) {
        List<ShapePoint> points = state.points();
        if (Minecraft.getInstance().level == null || points == null || points.size() < 2) return;

        Vec3 a = points.get(0).vec3(), b = points.get(1).vec3();
        Vec3 worldMin = new Vec3(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
        Vec3 worldMax = new Vec3(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
        sourceCenter = worldMin.add(worldMax).scale(0.5);
        sourceBounds = new AABB(worldMin, worldMax);
        Vec3 halfSize = worldMax.subtract(worldMin).scale(0.5);
        localMin = halfSize.scale(-1);
        localMax = halfSize;

        bakedMin = BlockPos.containing(worldMin.x, worldMin.y, worldMin.z);
        bakedMax = BlockPos.containing(worldMax.x - 1.0e-6, worldMax.y - 1.0e-6, worldMax.z - 1.0e-6);
        rebakeRegion();
        updateDestTransform();
        publishProjection();
    }

    private Boolean bakedExtended;

    protected static boolean irisExtendsNow() {
        return IrisCompat.isPackInUse() && IrisCompat.isRenderingLevel() && !IrisCompat.skipExtension();
    }

    private void rebakeRegion() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        bakedExtended = irisExtendsNow();
        drawFailed = false;
        blockEntityDrawFailed = false;
        bakeContent(level, bakedMin, bakedMax);
        AreaSuppression.set(shapeId, bakedMin, bakedMax);
    }

    protected void bakeContent(ClientLevel level, BlockPos min, BlockPos max) {
        AreaBaker.Result result = new AreaBaker().bake(level, min, max);
        solidMesh.upload(result.solidMesh());
        cutoutMesh.upload(result.cutoutMesh());
        translucentMesh.upload(result.translucentMesh());
        outlineVertices = result.outlineVertices();
        blockCount = result.blockCount();
        blockEntities = result.blockEntities();
    }

    /** Once per level frame, drawn or not: a subclass keeps its editor overlays up to date here. */
    protected void editorFrame() {}

    /** Runs before each draw is prepared; a subclass may rebuild its meshes here. */
    protected void beforePrepare() {}

    protected boolean forceTranslucent() {
        return false;
    }

    private Matrix4f destinationModel(Vec3 cameraPos) {
        return new Matrix4f(new Matrix4d().translation(-cameraPos.x, -cameraPos.y, -cameraPos.z).mul(destTransform).mul(meshToSource()));
    }

    @Override
    protected void generateRawGeometry(boolean regenerate) {
        double minX = localMin.x, minY = localMin.y, minZ = localMin.z;
        double maxX = localMax.x, maxY = localMax.y, maxZ = localMax.z;
        modelVertexes = List.of(new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ),
                new Vec3(maxX, maxY, minZ), new Vec3(minX, maxY, minZ),
                new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ));
        indexBuffer = new int[] {
                0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7,
                0, 1, 5, 0, 5, 4, 2, 3, 7, 2, 7, 6,
                1, 2, 6, 1, 6, 5, 0, 3, 7, 0, 7, 4};
    }

    private static final List<AreaShape> PREPARED = new ArrayList<>();
    private boolean drawFailed;
    private boolean blockEntityDrawFailed;
    private boolean frameTranslucent;
    private PreparedRenderType frameSolid;
    private PreparedRenderType frameCutout;
    private PreparedRenderType frameTranslucentMesh;

    @Override
    protected void drawInternal(VertexBuilder builder) {
        if (screen && ScreenLayer.isRendering()) drawOnScreen();
    }

    private void drawOnScreen() {
        if (AreaSuppression.consumeDirty(shapeId) || (bakedExtended != null && bakedExtended != irisExtendsNow())) {
            rebakeRegion();
        }
        if (baseColor.getAlpha() == 0) return;
        beforePrepare();
        Matrix4f model = ScreenLayer.origin().mul(new Matrix4f(new Matrix4d(destTransform).mul(meshToSource())));
        if ((!solidMesh.isEmpty() || !cutoutMesh.isEmpty() || !translucentMesh.isEmpty()) && !drawFailed) {
            drawSafely(() -> {
                // No fog on the UI layer: a zero fog transform puts every vertex at distance 0.
                prepareMesh(model, new Matrix4f().zero(), !IrisCompat.isPackInUse());
                RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "vector3_area_screen", target.getColorTextureView(), Optional.empty(),
                        target.hasDepth() ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    drawOpaqueMeshes(pass);
                    drawTranslucentMesh(pass);
                }
            });
        }
        if (!blockEntities.isEmpty() && !blockEntityDrawFailed) {
            try {
                Minecraft minecraft = Minecraft.getInstance();
                SubmitNodeStorage storage = new SubmitNodeStorage();
                submitBlockEntities(storage, minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState, model);
                Helpers.renderFeatures(minecraft, storage);
            } catch (Exception exception) {
                blockEntityDrawFailed = true;
                Vector3.LOGGER.warn("AreaShape block entity draw failed, pausing them until the next rebake", exception);
            }
        }
    }

    public static void beginFrame() {
        PREPARED.clear();
    }

    public void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
        editorFrame();
        if (!enabled() || screen) return;
        if (AreaSuppression.consumeDirty(shapeId) || (bakedExtended != null && bakedExtended != irisExtendsNow())) {
            rebakeRegion();
        }
        if (baseColor.getAlpha() == 0) return;
        beforePrepare();
        Matrix4f model = destinationModel(camera.pos);
        if ((!solidMesh.isEmpty() || !cutoutMesh.isEmpty() || !translucentMesh.isEmpty()) && !drawFailed) {
            try {
                prepareMesh(model, model, !IrisCompat.isPackInUse());
                PREPARED.add(this);
            } catch (Exception exception) {
                drawFailed = true;
                Vector3.LOGGER.warn("AreaShape draw failed, pausing its draws until the next rebake", exception);
            }
        }
        if (!blockEntities.isEmpty() && !blockEntityDrawFailed) {
            try {
                submitBlockEntities(collector, camera, model);
            } catch (Exception exception) {
                blockEntityDrawFailed = true;
                Vector3.LOGGER.warn("AreaShape block entity draw failed, pausing them until the next rebake", exception);
            }
        }
    }

    private void submitBlockEntities(SubmitNodeCollector collector, CameraRenderState camera, Matrix4f model) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float alpha = baseColor.getAlpha() / 255f;
        SubmitNodeCollector submits = alpha < 1.0f ? new AreaTintedCollector(collector, alpha) : collector;
        Matrix4f linear = new Matrix4f(model).setTranslation(0, 0, 0);
        AreaSuppression.bypassing(() -> {
            for (BlockEntity blockEntity : blockEntities) {
                BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, partialTick, null, false);
                if (state == null) continue;
                BlockPos pos = blockEntity.getBlockPos();
                Vector3f destPos = model.transformPosition(new Vector3f(pos.getX(), pos.getY(), pos.getZ()), new Vector3f());
                PoseStack poseStack = new PoseStack();
                poseStack.translate(destPos.x, destPos.y, destPos.z);
                poseStack.mulPose(linear);
                dispatcher.submit(state, poseStack, submits, camera);
            }
        });
    }

    // Only core/area_block reads fogModel from TextureMat; every other program (Iris's too) takes it as the UV transform.
    private void prepareMesh(Matrix4f model, Matrix4f fogModel, boolean ownShader) {
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(model);
        Vector4f colorModulator = colorToVector4f(baseColor);
        frameTranslucent = colorModulator.w() < 1.0f || forceTranslucent();
        if (!translucentMesh.isEmpty()) {
            translucentMesh.resort(new Matrix4f(model).invert().transformPosition(new Vector3f()));
        }
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
                modelView, colorModulator, new Vector3f(), ownShader ? fogModel : new Matrix4f());
        RenderType translucent = AreaRenderType.get(ownShader);
        frameSolid = solidMesh.isEmpty() ? null
                : withTransform(frameTranslucent ? translucent : AreaRenderType.getSolid(ownShader), transform);
        frameCutout = cutoutMesh.isEmpty() ? null
                : withTransform(frameTranslucent ? translucent : AreaRenderType.getCutout(ownShader), transform);
        frameTranslucentMesh = translucentMesh.isEmpty() ? null : withTransform(translucent, transform);
        reserveSequentialIndices(solidMesh);
        reserveSequentialIndices(cutoutMesh);
    }

    private static void reserveSequentialIndices(AreaGpuMesh mesh) {
        if (mesh.isEmpty()) return;
        var sequentialIndices = RenderSystem.getSequentialBuffer(mesh.topology());
        sequentialIndices.requestIndexCount(mesh.indexCount());
        sequentialIndices.resizeToRequestedIndexCount();
    }

    private static PreparedRenderType withTransform(RenderType renderType, GpuBufferSlice transform) {
        PreparedRenderType prepared = renderType.prepare();
        return new PreparedRenderType(prepared.name(), prepared.pipeline(), prepared.oitPipelineSet(), transform,
                prepared.scissorState(), prepared.textures());
    }

    public static void drawPreparedOpaque(RenderPass pass) {
        for (AreaShape area : PREPARED) {
            if (!area.frameTranslucent) area.drawSafely(() -> area.drawOpaqueMeshes(pass));
        }
    }

    public static void drawPreparedTranslucent(RenderPass pass) {
        for (AreaShape area : PREPARED) {
            area.drawSafely(() -> {
                if (area.frameTranslucent) area.drawOpaqueMeshes(pass);
                area.drawTranslucentMesh(pass);
            });
        }
        PREPARED.clear();
    }

    public static void drawPreparedTranslucent() {
        if (PREPARED.isEmpty()) return;
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_area", target.getColorTextureView(), Optional.empty(),
                target.hasDepth() ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            drawPreparedTranslucent(pass);
        }
    }

    private void drawSafely(Runnable draw) {
        if (drawFailed) return;
        try {
            draw.run();
        } catch (Exception exception) {
            drawFailed = true;
            Vector3.LOGGER.warn("AreaShape draw failed, pausing its draws until the next rebake", exception);
        }
    }

    private void drawTranslucentMesh(RenderPass pass) {
        if (frameTranslucentMesh == null) return;
        frameTranslucentMesh.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(translucentMesh.vertexBuffer(),
                translucentMesh.indexBuffer(), translucentMesh.indexType(), 0, 0, translucentMesh.indexCount(),
                translucentMesh.topology()), pass);
    }

    private void drawOpaqueMeshes(RenderPass pass) {
        if (frameSolid != null) drawSequential(pass, frameSolid, solidMesh);
        if (frameCutout != null) drawSequential(pass, frameCutout, cutoutMesh);
    }

    private static void drawSequential(RenderPass pass, PreparedRenderType prepared, AreaGpuMesh mesh) {
        prepared.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(mesh.vertexBuffer(), null,
                RenderSystem.getSequentialBuffer(mesh.topology()).type(), 0, 0, mesh.indexCount(), mesh.topology()), pass);
    }

    /** Whether this shape has outline content to contribute this frame; checked by AreaOutlineSubmitMixin. */
    public boolean hasOutline() {
        return !screen && outlineEnabled && !outlineVertices.isEmpty();
    }

    /**
     * Resubmits the outline geometry through vanilla's own SubmitNodeStorage — the outline pipeline is
     * vanilla's glow/entity-outline RenderType, which only composites for geometry collected this way
     * (see AreaOutlineSubmitMixin); this shape's own manual RenderPass (used for solid/cutout/
     * translucent above) can't reach it. Called once per frame, per AreaShape with outline enabled, by
     * that mixin — not from drawInternal.
     */
    public void submitOutline(SubmitNodeStorage submits, Vec3 cameraPos) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(destinationModel(cameraPos));
        int argb = (Math.round(outlineColor.w() * 255) << 24) | (Math.round(outlineColor.x() * 255) << 16)
                | (Math.round(outlineColor.y() * 255) << 8) | Math.round(outlineColor.z() * 255);
        submits.submitCustomGeometry(poseStack, AreaRenderType.getOutline(), (pose, consumer) -> {
            for (AreaBaker.OutlineVertex v : outlineVertices) {
                consumer.addVertex(pose, v.x(), v.y(), v.z()).setColor(argb).setUv(v.u(), v.v());
            }
        });
    }

    private static Vector4f colorToVector4f(Color color) {
        return new Vector4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    @Override
    public void discard() {
        PREPARED.remove(this);
        AreaSuppression.clear(shapeId);
        AreaProjection.clear(shapeId);
        solidMesh.close();
        cutoutMesh.close();
        translucentMesh.close();
        super.discard();
    }
}
