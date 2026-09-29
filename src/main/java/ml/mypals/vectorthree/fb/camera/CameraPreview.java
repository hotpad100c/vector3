package ml.mypals.vectorthree.fb.camera;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPositionOrbit;
import com.moulberry.flashback.keyframe.change.KeyframeChangeFov;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTrackEntity;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImBoolean;
import ml.mypals.vectorthree.mc.compat.IrisCompat;
import ml.mypals.vectorthree.core.port.Ports;
import ml.mypals.vectorthree.fb.editor.PersistentWindow;
import ml.mypals.vectorthree.mc.camera.PreviewPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Set;


/** The camera preview window: captures the keyframed camera at the playhead and shows what {@link PreviewPass} renders from it. */
public final class CameraPreview {
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_camera_preview");
    private static boolean fresh;
    private static @Nullable String problem;
    private static final ImBoolean withShaders = new ImBoolean(false);
    private static final ImBoolean detachMainView = new ImBoolean(true);
    private static @Nullable PreviewPass.Shot shot;

    private CameraPreview() {}

    /** The keyframed camera of the last preview render, while the main view is detached from it. */
    public static @Nullable PreviewPass.Shot shot() {
        return detachesMainView() ? shot : null;
    }

    /** The main view ignores the camera keyframes; the preview's own pass and exports still use them. */
    public static boolean detachesMainView() {
        return detachMainView.get() && !PreviewPass.isRendering() && WINDOW.isOpen() && !Flashback.isExporting();
    }

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.camera_preview.title"), "", WINDOW.isOpen())) WINDOW.toggle();
    }

    private static final class Capture implements KeyframeHandler {
        private static final Set<Class<? extends KeyframeChange>> SUPPORTED = Set.of(KeyframeChangeCameraPosition.class,
                KeyframeChangeCameraPositionOrbit.class, KeyframeChangeTrackEntity.class, KeyframeChangeFov.class);
        @Nullable Vector3d position;
        float yaw, pitch, roll;
        float fov = Float.NaN;

        @Override public boolean supportsKeyframeChange(Class<? extends KeyframeChange> change) { return SUPPORTED.contains(change); }
        @Override public Minecraft getMinecraft() { return Minecraft.getInstance(); }
        @Override public boolean alwaysApplyLastKeyframe() { return true; }

        @Override
        public void applyCameraPosition(Vector3d position, double yaw, double pitch, double roll) {
            this.position = new Vector3d(position);
            this.yaw = (float) yaw;
            this.pitch = (float) pitch;
            this.roll = (float) roll;
        }

        @Override public void applyFov(float fov) { this.fov = fov; }
    }

    private record Pose(double x, double y, double z, double xo, double yo, double zo, double xOld, double yOld,
            double zOld, float yRot, float xRot, float yRotO, float xRotO, float yHeadRot, float yHeadRotO) {
        static Pose of(LocalPlayer player) {
            return new Pose(player.getX(), player.getY(), player.getZ(), player.xo, player.yo, player.zo,
                    player.xOld, player.yOld, player.zOld, player.getYRot(), player.getXRot(), player.yRotO,
                    player.xRotO, player.yHeadRot, player.yHeadRotO);
        }

        void restore(LocalPlayer player) {
            player.setPos(x, y, z);
            player.xo = xo;
            player.yo = yo;
            player.zo = zo;
            player.xOld = xOld;
            player.yOld = yOld;
            player.zOld = zOld;
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.yRotO = yRotO;
            player.xRotO = xRotO;
            player.yHeadRot = yHeadRot;
            player.yHeadRotO = yHeadRotO;
        }
    }

    /** Runs at the start of every rendered frame, after the frame was extracted from the editor's camera. */
    public static void beforeFrame() {
        if (PreviewPass.isRendering()) return;
        fresh = false;
        shot = null;
        if (!WINDOW.isOpen() || !ReplayUI.isActive() || Flashback.isExporting()) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null || player == null || minecraft.level == null || minecraft.getCameraEntity() != player) return;
        if (!PreviewPass.mainTargetReady()) return;
        if (IrisCompat.isPackInUse() && !withShaders.get()) {
            problem = "vector3.camera_preview.iris";
            return;
        }

        Capture capture = new Capture();
        // Paused, the server may still be seeking, but the cursor is already where the user put it.
        editorState.applyKeyframes(capture, (float) Ports.clock().effectTick());
        if (capture.position == null) {
            problem = "vector3.camera_preview.no_camera";
            return;
        }
        problem = null;

        ReplayVisuals visuals = editorState.replayVisuals;
        boolean overrideFov = visuals.overrideFov, overrideRoll = visuals.overrideRoll;
        float fovAmount = visuals.overrideFovAmount, rollAmount = visuals.overrideRollAmount;
        Pose saved = Pose.of(player);
        shot = PreviewPass.render(() -> {
            Vector3d at = capture.position;
            player.snapTo(at.x, at.y, at.z, capture.yaw, capture.pitch);
            player.setOldPosAndRot();
            player.yHeadRot = player.yHeadRotO = capture.yaw;
            if (!Float.isNaN(capture.fov)) visuals.setFov(capture.fov);
            visuals.overrideRoll = Math.abs(capture.roll) >= 0.01f;
            visuals.overrideRollAmount = visuals.overrideRoll ? capture.roll : 0;
        }, () -> {
            saved.restore(player);
            visuals.overrideFov = overrideFov;
            visuals.overrideFovAmount = fovAmount;
            visuals.overrideRoll = overrideRoll;
            visuals.overrideRollAmount = rollAmount;
        });
        fresh = true;
    }

    public static void render() {
        if (!WINDOW.isOpen()) {
            PreviewPass.release();
            return;
        }
        ImGui.setNextWindowSize(360, 240, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW.title(I18n.get("vector3.camera_preview.title")), WINDOW.open(),
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            ImGui.checkbox(I18n.get("vector3.camera_preview.detach"), detachMainView);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.camera_preview.detach.tooltip"));
            if (IrisCompat.isPackInUse()) {
                ImGui.checkbox(I18n.get("vector3.camera_preview.with_shaders"), withShaders);
                if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.camera_preview.with_shaders.tooltip"));
            }
            float width = ImGui.getContentRegionAvailX(), height = ImGui.getContentRegionAvailY();
            var view = PreviewPass.textureView();
            if (problem != null) {
                ImGui.textWrapped(I18n.get(problem));
            } else if (fresh && view != null && width > 1 && height > 1) {
                float aspect = PreviewPass.aspect();
                float w = Math.min(width, height * aspect), h = w / aspect;
                ImGui.setCursorPos(ImGui.getCursorPosX() + (width - w) / 2, ImGui.getCursorPosY() + (height - h) / 2);
                ImGui.image(ReplayUI.imguiRenderer.getTextureId(view), w, h, 0, 1, 1, 0);
            } else {
                ImGui.textDisabled(I18n.get("vector3.camera_preview.waiting"));
            }
        }
        ImGui.end();
        WINDOW.sync();
    }
}
