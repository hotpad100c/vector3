package ml.mypals.vectorthree.shape;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.tags.EmptyMesh;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;


public final class AreaShape extends Shape implements EmptyMesh {
    private final AreaGpuMesh solidMesh = new AreaGpuMesh();
    private final AreaGpuMesh cutoutMesh = new AreaGpuMesh();
    private final AreaTranslucentGpuMesh translucentMesh = new AreaTranslucentGpuMesh();
    // CPU-side, not a GPU mesh — see AreaBaker.Result's javadoc for why.
    private List<AreaBaker.OutlineVertex> outlineVertices = List.of();
    private final String shapeId;
    private int blockCount;
    private boolean outlineEnabled;
    private Vector4f outlineColor = new Vector4f(1, 1, 1, 1);
    private Vec3 localMin = new Vec3(-0.5, -0.5, -0.5);
    private Vec3 localMax = new Vec3(0.5, 0.5, 0.5);
    private Vec3 sourceCenter = Vec3.ZERO;
    private Vec3 destCenter = Vec3.ZERO;
    private final Quaternionf destRotation = new Quaternionf();
    private Vec3 destScale = new Vec3(1, 1, 1);
    private List<BlockEntity> blockEntities = List.of();

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
        destCenter = new Vec3(state.x(), state.y(), state.z());
        destRotation.identity().rotateXYZ((float) Math.toRadians(state.pitch()),
                (float) Math.toRadians(state.yaw()), (float) Math.toRadians(state.roll()));
        destScale = new Vec3(state.scaleX(), state.scaleY(), state.scaleZ());
        outlineEnabled = state.outline();
        outlineColor = colorToVector4f(new Color(state.outlineColor(), true));
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
        Vec3 halfSize = worldMax.subtract(worldMin).scale(0.5);
        localMin = halfSize.scale(-1);
        localMax = halfSize;

        bakedMin = BlockPos.containing(worldMin.x, worldMin.y, worldMin.z);
        bakedMax = BlockPos.containing(worldMax.x - 1.0e-6, worldMax.y - 1.0e-6, worldMax.z - 1.0e-6);
        rebakeRegion();
    }

    private void rebakeRegion() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        AreaBaker.Result result = new AreaBaker().bake(level, bakedMin, bakedMax);
        solidMesh.upload(result.solidMesh());
        cutoutMesh.upload(result.cutoutMesh());
        translucentMesh.upload(result.translucentMesh());
        outlineVertices = result.outlineVertices();
        blockCount = result.blockCount();
        blockEntities = result.blockEntities();
        AreaSuppression.set(shapeId, bakedMin, bakedMax);
    }

    private Matrix4f destinationModel(Vec3 cameraPos) {
        return new Matrix4f()
                .translate((float) (destCenter.x - cameraPos.x), (float) (destCenter.y - cameraPos.y),
                        (float) (destCenter.z - cameraPos.z))
                .rotate(destRotation)
                .scale((float) destScale.x, (float) destScale.y, (float) destScale.z)
                .translate((float) -sourceCenter.x, (float) -sourceCenter.y, (float) -sourceCenter.z);
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

    private boolean drawFailed;

    @Override
    protected void drawInternal(VertexBuilder builder) {
        if (AreaSuppression.consumeDirty(shapeId)) rebakeRegion();
        if ((!solidMesh.isEmpty() || !cutoutMesh.isEmpty() || !translucentMesh.isEmpty()) && !drawFailed) {
            try {
                drawMesh();
            } catch (Exception exception) {
                drawFailed = true;
                Vector3.LOGGER.warn("AreaShape draw failed, disabling further draws for this shape", exception);
            }
        }
        if (!blockEntities.isEmpty() && !blockEntityDrawFailed) {
            try {
                drawBlockEntities();
            } catch (Exception exception) {
                blockEntityDrawFailed = true;
                Vector3.LOGGER.warn("AreaShape block entity draw failed, disabling further attempts for this shape", exception);
            }
        }
    }

    private boolean blockEntityDrawFailed;

    private void drawBlockEntities() {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f model = destinationModel(cameraPos);

        CameraRenderState cameraRenderState = new CameraRenderState();
        cameraRenderState.pos = cameraPos;
        cameraRenderState.orientation = new Quaternionf(camera.rotation());

        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vector4f modulator = colorToVector4f(baseColor);
        boolean translucent = modulator.w() < 1.0f;
        SubmitNodeStorage submits = translucent ? new AreaTranslucentSubmitNodeStorage() : new SubmitNodeStorage();

        // The feature render below must stay inside the bypass too: Sodium tessellates block-model parts
        // at render time through NonTerrainBlockRenderContext#tesselateBlock with the block entity's
        // source position, which is inside this shape's own suppressed AABB.
        AreaSuppression.bypassing(() -> {
            for (BlockEntity blockEntity : blockEntities) {
                BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, partialTick, null, false);
                if (state == null) continue;
                BlockPos pos = blockEntity.getBlockPos();
                Vector3f destPos = model.transformPosition(new Vector3f(pos.getX(), pos.getY(), pos.getZ()), new Vector3f());
                PoseStack poseStack = new PoseStack();
                poseStack.translate(destPos.x, destPos.y, destPos.z);
                poseStack.rotate(destRotation);
                poseStack.scale((float) destScale.x, (float) destScale.y, (float) destScale.z);
                dispatcher.submit(state, poseStack, submits, cameraRenderState);
            }
            if (translucent) {
                AreaBlockEntityTranslucency.withModulator(modulator, () -> Helpers.renderFeatures(minecraft, submits));
            } else {
                Helpers.renderFeatures(minecraft, submits);
            }
        });
    }

    private void drawMesh() {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Matrix4f model = destinationModel(cameraPos);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(model);
        Vector4f colorModulator = colorToVector4f(baseColor);

        if (!translucentMesh.isEmpty()) {
            Vector3f meshSpaceViewPoint = new Matrix4f(model).invert().transformPosition(new Vector3f());
            translucentMesh.resort(meshSpaceViewPoint);
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_area", target.getColorTextureView(), Optional.empty(),
                target.hasDepth() ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            // Solid, then cutout, then the depth-sorted translucent bucket — matching vanilla's own
            // per-layer draw order. Draw order within solid/cutout doesn't matter, only across layers.
            if (!solidMesh.isEmpty()) {
                drawOpaque(pass, modelView, colorModulator, AreaRenderType.getSolid(), solidMesh);
            }
            if (!cutoutMesh.isEmpty()) {
                drawOpaque(pass, modelView, colorModulator, AreaRenderType.getCutout(), cutoutMesh);
            }
            if (!translucentMesh.isEmpty()) {
                drawTranslucent(pass, modelView, colorModulator);
            }
        }
    }

    /** Whether this shape has outline content to contribute this frame; checked by AreaOutlineSubmitMixin. */
    public boolean hasOutline() {
        return outlineEnabled && !outlineVertices.isEmpty();
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

    private void drawOpaque(RenderPass pass, Matrix4f modelView, Vector4f colorModulator, RenderType renderType, AreaGpuMesh mesh) {
        if (colorModulator.w() < 1.0f) renderType = AreaRenderType.get();
        PreparedRenderType prepared = renderType.prepare();
        var transformSlice = RenderSystem.getDynamicUniforms().writeTransform(
                modelView, colorModulator, new Vector3f(), new Matrix4f());
        PreparedRenderType tinted = new PreparedRenderType(prepared.name(), prepared.pipeline(),
                prepared.oitPipelineSet(), transformSlice, prepared.scissorState(), prepared.textures());

        var sequentialIndices = RenderSystem.getSequentialBuffer(mesh.topology());
        sequentialIndices.requestIndexCount(mesh.indexCount());
        sequentialIndices.resizeToRequestedIndexCount();
        StagedVertexBuffer.ExecuteInfo info = new StagedVertexBuffer.ExecuteInfo(mesh.vertexBuffer(), null,
                sequentialIndices.type(), 0, 0, mesh.indexCount(), mesh.topology());
        tinted.drawFromBuffer(info, pass);
    }

    private void drawTranslucent(RenderPass pass, Matrix4f modelView, Vector4f colorModulator) {
        PreparedRenderType prepared = AreaRenderType.get().prepare();
        var transformSlice = RenderSystem.getDynamicUniforms().writeTransform(
                modelView, colorModulator, new Vector3f(), new Matrix4f());
        PreparedRenderType tinted = new PreparedRenderType(prepared.name(), prepared.pipeline(),
                prepared.oitPipelineSet(), transformSlice, prepared.scissorState(), prepared.textures());

        StagedVertexBuffer.ExecuteInfo info = new StagedVertexBuffer.ExecuteInfo(translucentMesh.vertexBuffer(),
                translucentMesh.indexBuffer(), translucentMesh.indexType(), 0, 0,
                translucentMesh.indexCount(), translucentMesh.topology());
        tinted.drawFromBuffer(info, pass);
    }

    private static Vector4f colorToVector4f(Color color) {
        return new Vector4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    @Override
    public void discard() {
        AreaSuppression.clear(shapeId);
        solidMesh.close();
        cutoutMesh.close();
        translucentMesh.close();
        super.discard();
    }
}
