package ml.mypals.vectorthree.camera;

import ml.mypals.vectorthree.mixin.flashback.ReplayUIAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;


@Environment(EnvType.CLIENT)
public final class EditorCameraController {
    private static final double RETARGET_RANGE = 192.0;
    private static final double DEFAULT_FOCUS_DISTANCE = 8.0;

    private enum Drag { NONE, ORBIT, PAN }

    private ViewportCamera camera = new ViewportCamera();
    private Drag dragging = Drag.NONE;
    private boolean mouseWasDown;
    private boolean focusKeyWasDown;

    public void reset() {
        if (dragging != Drag.NONE) ReplayUI.imguiWindower.ungrab();
        dragging = Drag.NONE;
        mouseWasDown = false;
        focusKeyWasDown = false;
        camera = new ViewportCamera();
    }

    public void frame() {
        if (!ReplayUI.isActive()) return;

        suppressMovementKeys();

        Camera mcCamera = Minecraft.getInstance().gameRenderer.mainCamera();
        boolean inViewport = mouseInViewport();
        boolean mouseDownNow = InputHelper.isMouseDownRaw(0);

        if (dragging != Drag.NONE) {
            if (!mouseDownNow) {
                ReplayUI.imguiWindower.ungrab();
                dragging = Drag.NONE;
                mouseWasDown = false;
                return;
            }
            double dx = ReplayUI.imguiWindower.getGrabbedMouseDeltaX();
            double dy = ReplayUI.imguiWindower.getGrabbedMouseDeltaY();
            if (dragging == Drag.ORBIT) camera.orbit(dx, dy);
            else camera.pan(dx, dy, viewportHeight(), mcCamera.getFov());
            applyCamera(mcCamera);
            mouseWasDown = true;
            return;
        }

        if (ReplayUI.imguiWindower.isGrabbed()) ReplayUI.imguiWindower.ungrab();

        boolean focusPressed = keyJustPressed(InputConstants.KEY_F);
        boolean hotkeysAllowed = !ImGui.getIO().getWantTextInput() && (inViewport || !ImGui.isAnyItemActive());
        if (hotkeysAllowed && focusPressed) {
            focus(mcCamera);
        }

        boolean justPressed = mouseDownNow && !mouseWasDown;
        mouseWasDown = mouseDownNow;

        if (inViewport && justPressed) {
            if (syncCamera(mcCamera)) {
                dragging = InputHelper.isShiftDownRaw() ? Drag.PAN : Drag.ORBIT;
                ReplayUI.imguiWindower.setGrabbed(false, 0, -1, -1);
            }
            return;
        }

        if (inViewport) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0 && syncCamera(mcCamera)) {
                camera.dolly(wheel);
                applyCamera(mcCamera);
                return;
            }
        }

        if (camera.isPrimed()) applyCamera(mcCamera);
    }

    private boolean keyJustPressed(int key) {
        boolean down = InputConstants.isKeyDown(key);
        boolean wasDown = focusKeyWasDown;
        focusKeyWasDown = down;
        return down && !wasDown;
    }

    private void focus(Camera mcCamera) {
        Vec3 direction = mouseLookVector();
        if (direction == null) return;
        Vec3 eye = mcCamera.position();

        String shapeId = ShapeTrackRegistry.pickShape(new RayModelIntersection.Ray(eye, direction));
        if (shapeId != null) {
            ShapeState state = ShapeTrackRegistry.state(shapeId);
            if (state != null) {
                camera.reset(eye, shapeWorldPosition(state));
                applyCamera(mcCamera);
                return;
            }
        }

        Entity entity = mcCamera.entity();
        Level level = Minecraft.getInstance().level;
        if (entity == null || level == null) return;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(direction.scale(RETARGET_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        camera.reset(eye, Vec3.atCenterOf(hit.getBlockPos()));
        applyCamera(mcCamera);
    }

    private static Vec3 shapeWorldPosition(ShapeState state) {
        Matrix4f world = ShapeTrackRegistry.worldTransformOrIdentity(state.parentShapeId());
        Vector3f worldPos = world.transformPosition(new Vector3f((float) state.x(), (float) state.y(), (float) state.z()));
        return new Vec3(worldPos.x, worldPos.y, worldPos.z);
    }

    private boolean syncCamera(Camera mcCamera) {
        Entity entity = mcCamera.entity();
        if (entity == null) return false;
        Vec3 eye = mcCamera.position();
        Vec3 focus = camera.isPrimed() ? camera.focus() : defaultFocus(mcCamera, eye);
        camera.reset(eye, focus);
        return true;
    }

    private Vec3 defaultFocus(Camera mcCamera, Vec3 eye) {
        Vec3 direction = new Vec3(mcCamera.forwardVector());
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(direction.scale(RETARGET_RANGE)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mcCamera.entity()));
            if (hit.getType() == HitResult.Type.BLOCK) return Vec3.atCenterOf(hit.getBlockPos());
        }
        return eye.add(direction.scale(DEFAULT_FOCUS_DISTANCE));
    }

    private void applyCamera(Camera mcCamera) {
        Entity entity = mcCamera.entity();
        if (entity == null || !camera.isPrimed()) return;
        Vec3 eye = camera.eyePosition();
        entity.snapTo(eye.x, eye.y - entity.getEyeHeight(), eye.z, camera.yaw(), camera.pitch());
        entity.setYHeadRot(camera.yaw());
    }

    private static void suppressMovementKeys() {
        Options options = Minecraft.getInstance().options;
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keyShift.setDown(false);
    }

    private static int viewportHeight() {
        return Math.max(1, ReplayUI.viewportSizeY);
    }

    /** Inside the viewport rect and not under another ImGui window (e.g. a keyframe popup drawn on top
     *  of it). Uses Flashback's own game-view hover flag rather than MouseHandledBy, which reports ImGui
     *  for as long as any popup is open — the gizmo is used exactly while the keyframe popup is open. */
    private static boolean mouseInViewport() {
        var mouse = ReplayUI.getMouseViewportFraction();
        return ReplayUI.isActive() && mouse != null
                && mouse.x >= 0 && mouse.x <= 1 && mouse.y >= 0 && mouse.y <= 1
                && ReplayUIAccessor.vector3$isFrameHovered();
    }

    private static Vec3 mouseLookVector() {
        Vec3 direction = ReplayUI.getMouseLookVector();
        return direction != null ? direction : unboundedMouseLookVector();
    }


    private static Vec3 unboundedMouseLookVector() {
        if (ReplayUI.lastProjectionMatrix == null || ReplayUI.lastViewQuaternion == null) return null;
        var mouse = ReplayUI.getMouseViewportFraction();
        Vector4f projected = new Vector4f(mouse.x * 2 - 1, mouse.y * 2 - 1, 0, 1)
                .mul(new Matrix4f(ReplayUI.lastProjectionMatrix).invert());
        return ReplayUI.getMouseLookVectorFromForwards(
                new Vec3(projected.x, -projected.y, projected.z).normalize());
    }
}
