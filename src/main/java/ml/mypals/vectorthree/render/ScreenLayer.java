package ml.mypals.vectorthree.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.render.MainRender;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The UI layer: shapes marked {@code screen} are drawn after the level (and its post effects) with an orthographic
 * projection. Coordinates are 1080p reference pixels: the origin is the screen center, y points up, the screen is
 * always 1080 units tall and 1080 × aspect wide; larger z is in front.
 * <p>
 * RRK draws every enabled shape relative to the camera, so the world pass switches the UI shapes off and this pass
 * switches everything else off, then re-runs RRK's own draw with the ortho projection and the camera offset undone.
 */
public final class ScreenLayer {
    public static final float HEIGHT = 1080;
    public static final String SHAPE_FLAG = "vector3:screen_layer";
    private static final float Z_NEAR = 1000, Z_FAR = 11000, Z_CENTER = (Z_NEAR + Z_FAR) / 2;

    private static final ShapeHider WORLD_PASS = new ShapeHider();
    private static final ShapeHider SCREEN_PASS = new ShapeHider();
    private static final Projection PROJECTION = new Projection();
    private static ProjectionMatrixBuffer projectionBuffer;
    private static boolean rendering;
    private static final Matrix4f basePose = new Matrix4f();
    private static final Matrix4f origin = new Matrix4f();

    private ScreenLayer() {}

    public static boolean isRendering() {
        return rendering;
    }

    public static float width() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        return HEIGHT * Math.max(1, main.width) / Math.max(1, main.height);
    }

    public static boolean isScreenShape(Identifier id) {
        return ShapeTrackRegistry.onScreenLayer(id) || Vector3.GIZMO_EDITOR.ownsScreenOverlay(id);
    }

    /** Around RRK's world draw: the UI shapes sit it out. */
    public static void beginWorldPass() {
        if (!rendering) WORLD_PASS.hide(ScreenLayer::isScreenShape);
    }

    public static void endWorldPass() {
        if (!rendering) WORLD_PASS.restore();
    }

    /** After the level, before the fade and the GUI. */
    public static void render() {
        if (!ShapeTrackRegistry.hasScreenShapes() && !Vector3.GIZMO_EDITOR.isScreenSpace()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (!camera.isInitialized() || main.width <= 0 || main.height <= 0) return;

        if (main.hasDepth()) {
            assert main.getDepthTexture() != null;
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(main.getDepthTexture(), 0.0);
        }
        float width = width();
        PROJECTION.setupOrtho(Z_NEAR, Z_FAR, width, HEIGHT, false);
        if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("vector3_screen_layer");
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(PROJECTION), ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        rendering = true;
        SCREEN_PASS.hide(id -> !isScreenShape(id));
        try {
            // RRK subtracts the camera position from every shape; adding it back leaves the UI coordinates.
            Vec3 eye = camera.position();
            PoseStack pose = new PoseStack();
            pose.translate(width / 2, HEIGHT / 2, -Z_CENTER);
            origin.set(pose.last().pose());
            pose.translate(eye.x, eye.y, eye.z);
            basePose.set(pose.last().pose());
            MainRender.render(pose, camera, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            TextGlow.composite();
        } finally {
            SCREEN_PASS.restore();
            rendering = false;
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    /** During the UI pass: what RRK's pose stack starts from, for shapes that build their own transforms. */
    public static Matrix4f basePose() {
        return new Matrix4f(basePose);
    }

    /** During the UI pass: UI coordinates to view space, for shapes that know their UI-space transform. */
    public static Matrix4f origin() {
        return new Matrix4f(origin);
    }

    /** The mouse as an orthographic ray in UI coordinates, or null outside the viewport. */
    public static RayModelIntersection.Ray mouseRay() {
        var mouse = ReplayUI.getMouseViewportFraction();
        double x = (mouse.x - 0.5) * width(), y = (0.5 - mouse.y) * HEIGHT;
        return new RayModelIntersection.Ray(new Vec3(x, y, Z_FAR), new Vec3(0, 0, -1));
    }

    /** Scale of a gizmo that looks about 70 pixels big on the viewport. */
    public static double gizmoScale() {
        return 70.0 * HEIGHT / Math.max(1, ReplayUI.viewportSizeY);
    }
}
