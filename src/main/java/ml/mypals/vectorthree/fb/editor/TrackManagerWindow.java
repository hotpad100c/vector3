package ml.mypals.vectorthree.fb.editor;

import ml.mypals.vectorthree.fb.timeline.TrackManagement;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import net.minecraft.client.resources.language.I18n;


public final class TrackManagerWindow {
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_tracks");
    private TrackManagerWindow() {}

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.track.title"), "", WINDOW.isOpen())) WINDOW.toggle();
    }

    public static void render() {
        if (!WINDOW.isOpen()) return;
        ImGui.setNextWindowSize(360, 440, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW.title(I18n.get("vector3.track.title")), WINDOW.open())) {
            EditorState state = EditorStateManager.getCurrent();
            if (state != null) renderTracks(state);
        }
        ImGui.end();
        WINDOW.sync();
    }

    private static void renderTracks(EditorState state) {
        long stamp = state.acquireWrite();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            for (KeyframeTrack track : scene.keyframeTracks) renderTrack(scene, track, state);
        } finally { state.release(stamp); }
    }

    private static void renderTrack(EditorScene scene, KeyframeTrack track, EditorState state) {
        String name = track.customName == null || track.customName.isBlank()
                ? I18n.get(track.keyframeType.name()) : track.customName;
        ImGui.text(name);
        ImGui.sameLine();
        ImGui.pushID("track" + scene.keyframeTracks.indexOf(track));
        if (ImGui.smallButton(((TrackManagement.Holder) track).vector3$locked() ? "L" : "l")) {
            TrackManagement.Holder holder = (TrackManagement.Holder) track;
            holder.vector3$setLocked(!holder.vector3$locked()); state.markDirty();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(((TrackManagement.Holder) track).vector3$solo() ? "S" : "s")) {
            TrackManagement.Holder holder = (TrackManagement.Holder) track;
            holder.vector3$setSolo(!holder.vector3$solo()); state.markDirty();
        }
        ImGui.popID();
    }
}
