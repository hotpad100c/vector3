package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistory;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

public final class HistoryWindow {
    public interface HistoryView {
        List<EditorSceneHistoryEntry> vector3$entries();
        int vector3$position();
    }
    public interface SceneHistory { EditorSceneHistory vector3$history(); }

    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_history");
    private HistoryWindow() {}

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.history.title"), "", WINDOW.isOpen())) WINDOW.toggle();
    }

    public static void render() {
        if (!WINDOW.isOpen()) return;
        ImGui.setNextWindowSize(320, 420, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW.title(I18n.get("vector3.history.title")), WINDOW.open())) {
            EditorState state = EditorStateManager.getCurrent();
            if (state == null) ImGui.textDisabled(I18n.get("vector3.history.empty"));
            else renderHistory(state);
        }
        ImGui.end();
        WINDOW.sync();
    }

    private static void renderHistory(EditorState state) {
        long stamp = state.acquireWrite();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            HistoryView history = (HistoryView) ((SceneHistory) scene).vector3$history();
            List<EditorSceneHistoryEntry> entries = history.vector3$entries();
            int position = history.vector3$position();
            if (entries.isEmpty()) {
                ImGui.textDisabled(I18n.get("vector3.history.empty"));
                return;
            }
            if (ImGui.selectable(I18n.get("vector3.history.initial"), position == 0)) jump(scene, state, position, 0);
            for (int i = 0; i < entries.size(); i++) {
                String description = entries.get(i).description();
                if (description == null || description.isBlank()) description = I18n.get("vector3.history.change");
                int target = i + 1;
                if (ImGui.selectable(description + "###history" + i, position == target)) jump(scene, state, position, target);
            }
        } finally {
            state.release(stamp);
        }
    }

    private static void jump(EditorScene scene, EditorState state, int from, int target) {
        for (int i = from; i > target; i--) scene.undo(message -> {});
        for (int i = from; i < target; i++) scene.redo(message -> {});
        state.markDirty();
    }
}
