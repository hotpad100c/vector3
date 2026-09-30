package ml.mypals.vectorthree.fb;

import ml.mypals.vectorthree.core.port.FrameHooks;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.TrackingBodyPart;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import ml.mypals.vectorthree.core.entity.BodyPart;
import ml.mypals.vectorthree.core.port.EditorViewport;
import ml.mypals.vectorthree.core.port.Ports;
import ml.mypals.vectorthree.core.port.ReplayClock;
import ml.mypals.vectorthree.core.port.ShapeEditing;
import ml.mypals.vectorthree.core.port.Splines;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.core.shape.ShapeState;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframe;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframeType;
import ml.mypals.vectorthree.fb.shape.ShapeReparent;
import net.minecraft.resources.Identifier;
import ml.mypals.vectorthree.core.port.ViewPolicy;
import ml.mypals.vectorthree.fb.camera.CameraPreview;
import ml.mypals.vectorthree.fb.camera.shake.CameraShake;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec2;

import java.util.Map;

/** The Flashback end of the ports core code calls through. Everything here is the only place that asks Flashback. */
public final class FlashbackPorts {
    private FlashbackPorts() {}

    public static void install() {
        Ports.install(new Clock(), new Viewport(), new View(), new Editing(), new SplineImpl());
        FrameHooks.beforeFrame(Editors.FOCUS_GIZMO::beforeFrame);
        FrameHooks.beforeFrame(CameraPreview::beforeFrame);
    }

    /** The replay tick an export is at. Flashback's own counter starts at 0 on the export's first tick. */
    public static double exportReplayTick() {
        return Flashback.EXPORT_JOB.getSettings().startTick() + Flashback.EXPORT_JOB.getCurrentTickDouble();
    }

    public static TrackingBodyPart toFlashback(BodyPart part) {
        return TrackingBodyPart.valueOf(part.name());
    }

    public static BodyPart fromFlashback(TrackingBodyPart part) {
        return BodyPart.valueOf(part.name());
    }

    private static final class Clock implements ReplayClock {
        public boolean replayLoaded() { return Flashback.getReplayServer() != null; }

        public boolean paused() {
            ReplayServer server = Flashback.getReplayServer();
            return server == null || server.replayPaused;
        }

        public boolean exporting() { return Flashback.isExporting(); }

        public boolean exportTransparent() {
            return Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent();
        }

        public double exportFrameSeconds() {
            return Flashback.isExporting() && Flashback.EXPORT_JOB != null ? 1.0 / Flashback.EXPORT_JOB.getSettings().framerate() : 0;
        }

        public double exportTick() { return exportReplayTick(); }

        public double partialTick() {
            ReplayServer server = Flashback.getReplayServer();
            return server == null ? 0 : server.getPartialReplayTick();
        }

        public double cursorTick() { return TimelineWindow.getCursorTick(); }
    }

    private static final class Viewport implements EditorViewport {
        public boolean active() { return ReplayUI.isActive(); }

        public double mouseFractionX() {
            Vec2 mouse = ReplayUI.getMouseViewportFraction();
            return mouse.x;
        }

        public double mouseFractionY() {
            Vec2 mouse = ReplayUI.getMouseViewportFraction();
            return mouse.y;
        }

        public int heightPixels() { return ReplayUI.viewportSizeY; }
    }

    private static final class View implements ViewPolicy {
        public boolean mainViewDetached() { return CameraPreview.detachesMainView(); }

        public boolean focusOverlayActive() { return Editors.FOCUS_GIZMO.overlayActive(); }

        public @Nullable SkyOverride skyOverride() {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState == null) return null;
            ReplayVisuals visuals = editorState.replayVisuals;
            if (visuals.renderSky) return null;
            if (Ports.clock().exportTransparent()) return new SkyOverride(0, 0, 0, 0);
            float[] colour = visuals.skyColour;
            return new SkyOverride(colour[0], colour[1], colour[2], 1);
        }

        public @Nullable CameraOffset cameraShake() {
            if (!Flashback.isInReplay()) return null;
            CameraShake.Sample sample = CameraShake.current();
            if (sample == null || (sample.right() == 0 && sample.up() == 0 && sample.forward() == 0)) return null;
            return new CameraOffset(sample.forward(), sample.up(), -sample.right());
        }
    }

    private static final class Editing implements ShapeEditing {
        public @Nullable String selectedShapeId() { return Editors.GIZMO_EDITOR.selectedShapeId(); }

        public boolean screenSpace() { return Editors.GIZMO_EDITOR.isScreenSpace(); }

        public boolean ownsScreenOverlay(Identifier id) { return Editors.GIZMO_EDITOR.ownsScreenOverlay(id); }

        public @Nullable ShapeState stateAt(String shapeId, double tick) {
            return readScene(scene -> ShapeReparent.stateAt(scene, shapeId, (float) tick));
        }

        public int lastKeyframeTick(String shapeId) {
            Integer last = readScene(scene -> {
                for (KeyframeTrack track : scene.keyframeTracks) {
                    if (track.keyframeType != ShapeKeyframeType.INSTANCE || track.keyframesByTick.isEmpty()) continue;
                    if (track.keyframesByTick.firstEntry().getValue() instanceof ShapeKeyframe first
                            && first.value.shapeId().equals(shapeId)) return track.keyframesByTick.lastKey();
                }
                return null;
            });
            return last == null ? -1 : last;
        }

        // Not from inside applyKeyframes: the scene's lock isn't reentrant.
        private static <T> @Nullable T readScene(java.util.function.Function<EditorScene, T> read) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState == null) return null;
            long stamp = editorState.acquireRead();
            try {
                return read.apply(editorState.getCurrentScene(stamp));
            } finally {
                editorState.release(stamp);
            }
        }
    }

    private static final class SplineImpl implements Splines {
        public double catmullRom(float p0, float p1, float p2, float p3, float t1, float t2, float t3, float amount) {
            return CatmullRom.value(p0, p1, p2, p3, t1, t2, t3, amount);
        }

        public double hermite(Map<Float, Double> points, float at) {
            return Hermite.value(points, at);
        }
    }
}
