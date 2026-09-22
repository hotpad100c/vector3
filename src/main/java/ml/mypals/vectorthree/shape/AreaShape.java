package ml.mypals.vectorthree.shape;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.tags.EmptyMesh;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
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
    private final AreaGpuMesh mesh = new AreaGpuMesh();
    private final String shapeId;
    private int blockCount;
    private Vec3 localMin = new Vec3(-0.5, -0.5, -0.5);
    private Vec3 localMax = new Vec3(0.5, 0.5, 0.5);
    private Vec3 sourceCenter = Vec3.ZERO;
    private Vec3 destCenter = Vec3.ZERO;
    private final Quaternionf destRotation = new Quaternionf();
    private Vec3 destScale = new Vec3(1, 1, 1);

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

    /** Updates the destination transform without touching the bake; cheap, called every apply(). */
    public void updateTransform(ShapeState state) {
        destCenter = new Vec3(state.x(), state.y(), state.z());
        destRotation.identity().rotateXYZ((float) Math.toRadians(state.pitch()),
                (float) Math.toRadians(state.yaw()), (float) Math.toRadians(state.roll()));
        destScale = new Vec3(state.scaleX(), state.scaleY(), state.scaleZ());
    }

    private void bake(ShapeState state) {
        ClientLevel level = Minecraft.getInstance().level;
        List<ShapePoint> points = state.points();
        if (level == null || points == null || points.size() < 2) return;

        Vec3 a = points.get(0).vec3(), b = points.get(1).vec3();
        Vec3 worldMin = new Vec3(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
        Vec3 worldMax = new Vec3(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
        sourceCenter = worldMin.add(worldMax).scale(0.5);
        Vec3 halfSize = worldMax.subtract(worldMin).scale(0.5);
        localMin = halfSize.scale(-1);
        localMax = halfSize;

        BlockPos min = BlockPos.containing(worldMin.x, worldMin.y, worldMin.z);
        BlockPos max = BlockPos.containing(worldMax.x - 1.0e-6, worldMax.y - 1.0e-6, worldMax.z - 1.0e-6);

        AreaBaker.Result result = new AreaBaker().bake(level, min, max);
        mesh.upload(result.mesh());
        blockCount = result.blockCount();
        AreaSuppression.set(shapeId, min, max);
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
        // This manual RenderPass sequence is the riskiest, least-precedented part of AreaShape (no
        // other shape in this mod opens its own command encoder). If it ever throws, disable further
        // attempts for this instance instead of risking crashing the whole frame every frame.
        if (mesh.isEmpty() || drawFailed) return;
        try {
            drawMesh();
        } catch (Exception exception) {
            drawFailed = true;
            Vector3.LOGGER.warn("AreaShape draw failed, disabling further draws for this shape", exception);
        }
    }

    private void drawMesh() {
        RenderType renderType = AreaRenderType.get();
        PreparedRenderType prepared = renderType.prepare();
        Vector4f colorModulator = colorToVector4f(baseColor);

        // Baked vertices are literal absolute-world coordinates anchored at sourceCenter (see
        // AreaBaker/bake()). getModelViewMatrixCopy() is rotation-only here (confirmed: it maps the
        // camera's own world position to a nonzero point, not the origin), so vertices must already be
        // camera-relative before it's applied — fold that into the translation, refetched every frame
        // since the camera moves.
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Matrix4f model = new Matrix4f()
                .translate((float) (destCenter.x - cameraPos.x), (float) (destCenter.y - cameraPos.y),
                        (float) (destCenter.z - cameraPos.z))
                .rotate(destRotation)
                .scale((float) destScale.x, (float) destScale.y, (float) destScale.z)
                .translate((float) -sourceCenter.x, (float) -sourceCenter.y, (float) -sourceCenter.z);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(model);

        var transformSlice = RenderSystem.getDynamicUniforms().writeTransform(
                modelView, colorModulator, new Vector3f(), new Matrix4f());
        PreparedRenderType tinted = new PreparedRenderType(prepared.name(), prepared.pipeline(),
                prepared.oitPipelineSet(), transformSlice, prepared.scissorState(), prepared.textures());

        // No owned index buffer (see AreaGpuMesh) — draw against the shared sequential buffer for this
        // topology, the same one ExecuteInfo.indexBuffer() falls back to for a null custom buffer.
        var sequentialIndices = RenderSystem.getSequentialBuffer(mesh.topology());
        sequentialIndices.requestIndexCount(mesh.indexCount());
        sequentialIndices.resizeToRequestedIndexCount();
        StagedVertexBuffer.ExecuteInfo info = new StagedVertexBuffer.ExecuteInfo(
                mesh.vertexBuffer(), null, sequentialIndices.type(), 0, 0, mesh.indexCount(), mesh.topology());

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_area", target.getColorTextureView(), Optional.empty(),
                target.hasDepth() ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            tinted.drawFromBuffer(info, pass);
        }
    }

    private static Vector4f colorToVector4f(Color color) {
        return new Vector4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    @Override
    public void discard() {
        AreaSuppression.clear(shapeId);
        mesh.close();
        super.discard();
    }
}
