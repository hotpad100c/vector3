package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** TimelineWindow's private statics. Only fb/timeline/Timeline reads these; the rest of vector3 goes through it. */
@Mixin(value = TimelineWindow.class, remap = false)
public interface TimelineWindowAccessor {
    @Accessor("editorScene") static EditorScene vector3$scene() { throw new AssertionError(); }

    @Accessor("editorState") static EditorState vector3$state() { throw new AssertionError(); }

    @Accessor("selectedKeyframesList") static List<SelectedKeyframes> vector3$selected() { throw new AssertionError(); }

    @Accessor("editingKeyframeTrack") static int vector3$editingTrack() { throw new AssertionError(); }

    @Accessor("editingKeyframeTrack") static void vector3$setEditingTrack(int track) { throw new AssertionError(); }

    @Accessor("editingKeyframeTick") static int vector3$editingTick() { throw new AssertionError(); }

    @Accessor("editingKeyframeTick") static void vector3$setEditingTick(int tick) { throw new AssertionError(); }

    @Accessor("x") static float vector3$x() { throw new AssertionError(); }

    @Accessor("y") static float vector3$y() { throw new AssertionError(); }

    @Accessor("width") static float vector3$width() { throw new AssertionError(); }

    @Accessor("height") static float vector3$height() { throw new AssertionError(); }

    @Accessor("mouseX") static float vector3$mouseX() { throw new AssertionError(); }

    @Accessor("mouseY") static float vector3$mouseY() { throw new AssertionError(); }

    @Accessor("keyframeSize") static int vector3$keyframeSize() { throw new AssertionError(); }

    @Accessor("openCreateKeyframeAtTickTrack") static int vector3$openCreateAtTrack() { throw new AssertionError(); }

    @Accessor("repositioningKeyframeTrack") static int vector3$repositioningTrack() { throw new AssertionError(); }

    @Accessor("repositioningKeyframeTrack") static void vector3$setRepositioningTrack(int track) { throw new AssertionError(); }

    @Accessor("dragStartMouseY") static float vector3$dragStartMouseY() { throw new AssertionError(); }

    @Accessor("grabbedKeyframe") static boolean vector3$grabbedKeyframe() { throw new AssertionError(); }

    @Accessor("grabbedKeyframeTrack") static int vector3$grabbedTrack() { throw new AssertionError(); }

    @Invoker("upgradeToSceneWrite") static void vector3$upgradeToSceneWrite() { throw new AssertionError(); }

    @Invoker("timelineXToReplayTick") static int vector3$tickAt(float x) { throw new AssertionError(); }

    @Invoker("replayTickToTimelineX") static int vector3$xOf(int tick) { throw new AssertionError(); }
}
