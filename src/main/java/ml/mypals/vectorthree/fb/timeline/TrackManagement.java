package ml.mypals.vectorthree.fb.timeline;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import net.minecraft.client.resources.language.I18n;


public final class TrackManagement {
    public interface Holder {
        boolean vector3$locked();
        void vector3$setLocked(boolean value);
        boolean vector3$solo();
        void vector3$setSolo(boolean value);
    }

    private static EditorScene activeScene;
    private TrackManagement() {}
    public static void useScene(EditorScene scene) { activeScene = scene; }
    public static boolean locked(KeyframeTrack track) { return ((Holder) track).vector3$locked(); }
    // Solo mutes the other tracks of its own keyframe type only: soloing a pose track must not silence the camera.
    public static boolean audible(KeyframeTrack track) {
        if (activeScene == null || ((Holder) track).vector3$solo()) return true;
        for (KeyframeTrack other : activeScene.keyframeTracks) {
            if (other.keyframeType == track.keyframeType && ((Holder) other).vector3$solo()) return false;
        }
        return true;
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
        return changed;
    }
}
