package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;

import java.util.LinkedHashSet;
import java.util.Set;

public final class TrackManagement {
    public interface Holder {
        boolean vector3$locked();
        void vector3$setLocked(boolean value);
        boolean vector3$solo();
        void vector3$setSolo(boolean value);
        boolean vector3$collapsed();
        void vector3$setCollapsed(boolean value);
        String vector3$folder();
        void vector3$setFolder(String value);
    }

    private static EditorScene activeScene;
    private TrackManagement() {}
    public static void useScene(EditorScene scene) { activeScene = scene; }
    public static boolean locked(KeyframeTrack track) { return ((Holder) track).vector3$locked(); }
    public static boolean audible(KeyframeTrack track) {
        if (activeScene == null) return true;
        boolean hasSolo = activeScene.keyframeTracks.stream().anyMatch(t -> ((Holder) t).vector3$solo());
        return !hasSolo || ((Holder) track).vector3$solo();
    }

    public static boolean menu(EditorScene scene, KeyframeTrack track) {
        Holder holder = (Holder) track;
        boolean changed = false;
        if (ImGui.menuItem(I18n.get("vector3.track.lock"), "", holder.vector3$locked())) {
            holder.vector3$setLocked(!holder.vector3$locked()); changed = true;
        }
        if (ImGui.menuItem(I18n.get("vector3.track.solo"), "", holder.vector3$solo())) {
            holder.vector3$setSolo(!holder.vector3$solo()); changed = true;
        }
        if (ImGui.menuItem(I18n.get("vector3.track.collapse"), "", holder.vector3$collapsed())) {
            holder.vector3$setCollapsed(!holder.vector3$collapsed()); changed = true;
        }
        if (ImGui.beginMenu(I18n.get("vector3.track.folder"))) {
            String folder = holder.vector3$folder();
            if (ImGui.menuItem(I18n.get("vector3.track.no_folder"), "", folder == null || folder.isBlank())) {
                holder.vector3$setFolder(null); changed = true;
            }
            Set<String> folders = new LinkedHashSet<>();
            for (KeyframeTrack other : scene.keyframeTracks) {
                String value = ((Holder) other).vector3$folder();
                if (value != null && !value.isBlank()) folders.add(value);
            }
            for (String value : folders) if (ImGui.menuItem(value, "", value.equals(folder))) {
                holder.vector3$setFolder(value); changed = true;
            }
            if (ImGui.menuItem(I18n.get("vector3.track.new_folder"))) {
                holder.vector3$setFolder(I18n.get("vector3.track.folder_name", folders.size() + 1)); changed = true;
            }
            ImGui.endMenu();
        }
        return changed;
    }
}
