package ml.mypals.vectorthree.fb.timeline;

import ml.mypals.vectorthree.fb.editor.EditorInput;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import ml.mypals.vectorthree.mixin.flashback.TimelineWindowAccessor;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Flashback's timeline window as the rest of vector3 sees it. Everything that reads or writes TimelineWindow's private
 * state goes through here, so a change in Flashback's timeline touches this class and {@link TimelineWindowAccessor} only.
 */
public final class Timeline {
    private Timeline() {}

    /** Only non-null while the timeline is rendering. */
    public static EditorScene scene() { return TimelineWindowAccessor.vector3$scene(); }

    public static EditorState state() { return TimelineWindowAccessor.vector3$state(); }

    public static List<SelectedKeyframes> selected() { return TimelineWindowAccessor.vector3$selected(); }

    public static int editingTrack() { return TimelineWindowAccessor.vector3$editingTrack(); }

    public static int editingTick() { return TimelineWindowAccessor.vector3$editingTick(); }

    public static void setEditingTrack(int track) { TimelineWindowAccessor.vector3$setEditingTrack(track); }

    public static void setEditingTick(int tick) { TimelineWindowAccessor.vector3$setEditingTick(tick); }

    public static void setEditing(int track, int tick) {
        setEditingTrack(track);
        setEditingTick(tick);
    }

    /** The timeline's screen rectangle and mouse position. */
    public static float x() { return TimelineWindowAccessor.vector3$x(); }

    public static float y() { return TimelineWindowAccessor.vector3$y(); }

    public static float width() { return TimelineWindowAccessor.vector3$width(); }

    public static float height() { return TimelineWindowAccessor.vector3$height(); }

    public static float mouseX() { return TimelineWindowAccessor.vector3$mouseX(); }

    public static float mouseY() { return TimelineWindowAccessor.vector3$mouseY(); }

    public static int keyframeSize() { return TimelineWindowAccessor.vector3$keyframeSize(); }

    public static int openCreateAtTrack() { return TimelineWindowAccessor.vector3$openCreateAtTrack(); }

    public static int repositioningTrack() { return TimelineWindowAccessor.vector3$repositioningTrack(); }

    public static void setRepositioningTrack(int track) { TimelineWindowAccessor.vector3$setRepositioningTrack(track); }

    public static float dragStartMouseY() { return TimelineWindowAccessor.vector3$dragStartMouseY(); }

    public static boolean grabbedKeyframe() { return TimelineWindowAccessor.vector3$grabbedKeyframe(); }

    public static int grabbedTrack() { return TimelineWindowAccessor.vector3$grabbedTrack(); }

    /** Call before mutating the scene inside the timeline. */
    public static void upgradeToSceneWrite() { TimelineWindowAccessor.vector3$upgradeToSceneWrite(); }

    /** A timeline-relative x to a replay tick, and back. */
    public static int tickAt(float timelineX) { return TimelineWindowAccessor.vector3$tickAt(timelineX); }

    public static int xOf(int tick) { return TimelineWindowAccessor.vector3$xOf(tick); }

    public static boolean mouseInTimeline() {
        return !EditorInput.isMainFrameHovered()
                && mouseX() >= x() && mouseX() < x() + width()
                && mouseY() >= y() && mouseY() < y() + height();
    }

    public static void clearKeyframeSelection() {
        selected().clear();
        setEditing(-1, -1);
    }

    /** After changing keyframes: mark the replay dirty and re-apply them. */
    public static void keyframesChanged() {
        state().markDirty();
        ((MinecraftExt) Minecraft.getInstance()).flashback$applyKeyframes();
    }
}
