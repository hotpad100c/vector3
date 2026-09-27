package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.vertex.BufferBuilder;
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
 * An area whose blocks fly apart to a scatter cloud, or gather from it, along a trajectory. The meshes are rebuilt
 * on the CPU whenever the progress (or anything else) changes, in coordinates relative to the region's center.
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
    private Matrix4f[] transforms;
    private float[] alphas;
    private boolean translucentFrame;
    private Object builtKey;
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
        transforms = new Matrix4f[count];
        alphas = new float[count];
        for (int i = 0; i < count; i++) {
            BlockPos pos = pieces.get(i).pos();
            gx[i] = pos.getX() - min.getX();
            gy[i] = pos.getY() - min.getY();
            gz[i] = pos.getZ() - min.getZ();
            centers[i] = new Vector3f((float) (pos.getX() + 0.5 - center.x), (float) (pos.getY() + 0.5 - center.y),
                    (float) (pos.getZ() + 0.5 - center.z));
            transforms[i] = new Matrix4f();
        }
        motion = new BlastMotion(gx, gy, gz, centers);
        builtKey = null;
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
        motion.evaluate(settings, pathStart, path, origin, sweepEnd, scatter, progress, transforms, alphas);
        if (buffers == null) buffers = new ByteBufferBuilder[]{new ByteBufferBuilder(1 << 16), new ByteBufferBuilder(1 << 12), new ByteBufferBuilder(1 << 12)};
        BufferBuilder solid = builder(buffers[0], AreaRenderType.getSolid());
        BufferBuilder cutout = builder(buffers[1], AreaRenderType.getCutout());
        BufferBuilder translucent = builder(buffers[2], AreaRenderType.get());
        Vec3 center = sourceCenter();
        boolean anyTranslucent = false;
        Vector3f position = new Vector3f(), normal = new Vector3f();
        Matrix3f normalMatrix = new Matrix3f();
        for (int i = 0; i < pieces.size(); i++) {
            float alpha = alphas[i];
            if (alpha <= 0.001f) continue;
            if (alpha < 0.999f) anyTranslucent = true;
            BlastBaker.Piece piece = pieces.get(i);
            Matrix4f transform = transforms[i];
            normalMatrix.set(transform).normal();
            float ox = (float) (piece.pos().getX() - center.x), oy = (float) (piece.pos().getY() - center.y),
                    oz = (float) (piece.pos().getZ() - center.z);
            write(solid, piece.solid(), transform, normalMatrix, ox, oy, oz, alpha, position, normal);
            write(cutout, piece.cutout(), transform, normalMatrix, ox, oy, oz, alpha, position, normal);
            write(translucent, piece.translucent(), transform, normalMatrix, ox, oy, oz, alpha, position, normal);
        }
        translucentFrame = anyTranslucent;
        solidMesh.upload(solid.build());
        cutoutMesh.upload(cutout.build());
        translucentMesh.upload(translucent.build());
    }

    private static BufferBuilder builder(ByteBufferBuilder bytes, RenderType type) {
        return new BufferBuilder(bytes, type.primitiveTopology(), type.format());
    }

    private static void write(BufferBuilder builder, BlastBaker.Layer layer, Matrix4f transform, Matrix3f normalMatrix,
            float ox, float oy, float oz, float alpha, Vector3f position, Vector3f normal) {
        int vertices = layer.vertexCount();
        if (vertices == 0) return;
        float[] data = layer.data.elements();
        int[] colors = layer.colors.elements();
        int[] lights = layer.lights.elements();
        for (int v = 0; v < vertices; v++) {
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
        super.discard();
    }
}
