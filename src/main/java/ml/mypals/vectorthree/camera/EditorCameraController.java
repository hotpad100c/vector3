package ml.mypals.vectorthree.camera;

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
    /** Our own edge-detected mouse/key state — ImGui's own isMouseClicked()/isMouseDoubleClicked()
     *  go stale across an EDITOR_GRABBED cursor-capture cycle (see frame() below), so drag start/end
     *  can't be trusted to ImGui here the way the rest of the codebase normally would. */
    private boolean mouseWasDown;
    private boolean focusKeyWasDown;

    /** Drops any drag in progress and forgets the current focus point, so a stale orbit target from
     *  one replay never leaks into the next. */
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
            // ImGui's own mouse delta stops updating while EDITOR_GRABBED — the grabbed delta is the
            // same primitive Flashback's own free-look reads while its cursor is captured.
            double dx = ReplayUI.imguiWindower.getGrabbedMouseDeltaX();
            double dy = ReplayUI.imguiWindower.getGrabbedMouseDeltaY();
            if (dragging == Drag.ORBIT) camera.orbit(dx, dy);
            else camera.pan(dx, dy, viewportHeight(), mcCamera.getFov());
            applyCamera(mcCamera);
            mouseWasDown = true;
            return;
        }

        // Flashback's own free-look only engages in MouseHandledBy.GAME; make sure we're not sitting
        // in that state (e.g. left over from before Editor Mode was toggled on) while otherwise idle.
        if (ReplayUI.imguiWindower.isGrabbed()) ReplayUI.imguiWindower.ungrab();

        boolean hotkeysAllowed = !ImGui.getIO().getWantTextInput() && !ImGui.isAnyItemActive();
        if (inViewport && hotkeysAllowed && keyJustPressed(InputConstants.KEY_F)) {
            focus(mcCamera);
        }

        // Raw edge detection instead of ImGui.isMouseClicked(0): right after ungrab() above, ImGui's
        // own click bookkeeping is one frame stale from having been shut out during EDITOR_GRABBED, so
        // isMouseClicked() swallows the first real click back — the user has to click twice to resume
        // dragging. Tracking the raw down/up edge ourselves sidesteps that staleness entirely.
        boolean justPressed = mouseDownNow && !mouseWasDown;
        mouseWasDown = mouseDownNow;

        if (inViewport && justPressed) {
            if (syncCamera(mcCamera)) {
                dragging = InputHelper.isShiftDownRaw() ? Drag.PAN : Drag.ORBIT;
                // EDITOR_GRABBED hides/captures the cursor (comfortable for a drag) without letting
                // Flashback's own vanilla camera-look see the resulting deltas.
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

        // Idle: keep holding the last computed pose every frame, not just while actively dragging —
        // otherwise Flashback's own flight (WASD/jump/shift, or plain gravity) can still nudge the
        // entity between drags, since key suppression alone isn't an airtight guarantee.
        if (camera.isPrimed()) applyCamera(mcCamera);
    }

    private boolean keyJustPressed(int key) {
        boolean down = InputConstants.isKeyDown(key);
        boolean wasDown = focusKeyWasDown;
        focusKeyWasDown = down;
        return down && !wasDown;
    }

    /** F focuses the orbit camera on whatever's under the cursor — a vector3 shape first (reusing the
     *  same pick vector3's gizmo/select tooling already uses), falling back to a world block. */
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

    /** Forces vanilla's WASD/Space/Shift flight keys to read as released every frame, so Flashback's
     *  normal movement can't fight the entity position this class is setting via applyCamera(). This
     *  has to be repeated every frame (not just once on entry) since a real GLFW key-down callback
     *  would otherwise flip the mapping back to "held" as long as the physical key stays pressed. */
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

    private static boolean mouseInViewport() {
        var mouse = ReplayUI.getMouseViewportFraction();
        return ReplayUI.isActive() && mouse != null
                && mouse.x >= 0 && mouse.x <= 1 && mouse.y >= 0 && mouse.y <= 1;
    }

    private static Vec3 mouseLookVector() {
        Vec3 direction = ReplayUI.getMouseLookVector();
        return direction != null ? direction : unboundedMouseLookVector();
    }


    private static Vec3 unboundedMouseLookVector() {
        if (ReplayUI.lastProjectionMatrix == null || ReplayUI.lastViewQuaternion == null) return null;
        var mouse = ReplayUI.getMouseViewportFraction();
        if (mouse == null) return null;
        Vector4f projected = new Vector4f(mouse.x * 2 - 1, mouse.y * 2 - 1, 0, 1)
                .mul(new Matrix4f(ReplayUI.lastProjectionMatrix).invert());
        return ReplayUI.getMouseLookVectorFromForwards(
                new Vec3(projected.x, -projected.y, projected.z).normalize());
    }
}
