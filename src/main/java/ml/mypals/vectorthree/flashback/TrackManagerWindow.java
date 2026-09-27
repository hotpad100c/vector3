package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiTreeNodeFlags;
import net.minecraft.client.resources.language.I18n;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            Map<String, List<KeyframeTrack>> folders = new LinkedHashMap<>();
            for (KeyframeTrack track : scene.keyframeTracks) {
                String folder = ((TrackManagement.Holder) track).vector3$folder();
                folders.computeIfAbsent(folder == null ? "" : folder, key -> new java.util.ArrayList<>()).add(track);
            }
            for (Map.Entry<String, List<KeyframeTrack>> entry : folders.entrySet()) {
                if (entry.getKey().isEmpty()) for (KeyframeTrack track : entry.getValue()) renderTrack(scene, track, state);
                else {
                    TrackManagement.Holder folder = (TrackManagement.Holder) entry.getValue().getFirst();
                    int flags = folder.vector3$collapsed() ? 0 : ImGuiTreeNodeFlags.DefaultOpen;
                    boolean open = ImGui.treeNodeEx(entry.getKey() + "###folder" + entry.getKey(), flags);
                    folder.vector3$setCollapsed(!open);
                    if (open) {
                        for (KeyframeTrack track : entry.getValue()) renderTrack(scene, track, state);
                        ImGui.treePop();
                    }
                }
            }
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
