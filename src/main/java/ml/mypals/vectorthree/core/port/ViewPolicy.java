package ml.mypals.vectorthree.core.port;

import org.jetbrains.annotations.Nullable;

/** How the editor wants the game view drawn. Implemented on the Flashback side, asked from render code. */
public interface ViewPolicy {
    /** The main view ignores the camera keyframes (the camera preview window shows them instead). */
    boolean mainViewDetached();

    boolean focusOverlayActive();

    /** The flat colour the editor draws instead of the sky, or null while the sky renders normally. */
    @Nullable SkyOverride skyOverride();

    /** Extra camera movement from shake keyframes, or null. */
    @Nullable CameraOffset cameraShake();

    record SkyOverride(float r, float g, float b, float a) {}

    record CameraOffset(float forward, float up, float left) {}

    ViewPolicy NONE = new ViewPolicy() {
        public boolean mainViewDetached() { return false; }
        public boolean focusOverlayActive() { return false; }
        public @Nullable SkyOverride skyOverride() { return null; }
        public @Nullable CameraOffset cameraShake() { return null; }
    };
}
