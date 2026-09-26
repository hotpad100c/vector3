package ml.mypals.vectorthree.clips;

import com.moulberry.flashback.Flashback;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import ml.mypals.vectorthree.flashback.PersistentWindow;
import net.minecraft.client.resources.language.I18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Lists the recorded replays; drag one onto the timeline to add it as a clip. */
public final class ClipsWindow {
    public static final String PAYLOAD = "vector3_clip";
    private static final PersistentWindow WINDOW = new PersistentWindow("vector3_clips");
    private static final long RESCAN_MILLIS = 2000;

    private static List<Path> replays = List.of();
    private static long lastScan;
    private static boolean applyRequested;

    private ClipsWindow() {}

    public static void renderMenuItem() {
        if (ImGui.menuItem(I18n.get("vector3.clips.title"), "", WINDOW.isOpen())) WINDOW.toggle();
    }

    public static boolean consumeApplyRequest() {
        boolean requested = applyRequested;
        applyRequested = false;
        return requested;
    }

    public static void render(boolean dirty) {
        if (!WINDOW.isOpen()) return;
        ImGui.setNextWindowSize(300, 360, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW.title(I18n.get("vector3.clips.title")), WINDOW.open())) {
            if (ClipProject.isComposing()) {
                ImGui.textDisabled(I18n.get("vector3.clips.composing"));
            } else {
                if (!dirty) ImGui.beginDisabled();
                if (ImGui.button(I18n.get("vector3.clips.apply"))) applyRequested = true;
                if (!dirty) ImGui.endDisabled();
                if (dirty) {
                    ImGui.sameLine();
                    ImGui.textColored(0xFF40A0FF, I18n.get("vector3.clips.pending"));
                }
            }
            ImGui.textDisabled(I18n.get("vector3.clips.drag_hint"));
            ImGui.separator();
            for (Path replay : replays()) {
                ReplayArchive.Info info = ReplayArchive.read(replay);
                if (info == null) continue;
                String name = info.meta().name == null || info.meta().name.isBlank()
                        ? replay.getFileName().toString() : info.meta().name;
                ImGui.selectable(name + "  " + duration(info.totalTicks()) + "##" + replay);
                if (ImGui.isItemHovered()) ImGui.setTooltip(replay.toString());
                if (ImGui.beginDragDropSource()) {
                    ImGui.setDragDropPayload(PAYLOAD, replay.toString());
                    ImGui.text(name);
                    ImGui.endDragDropSource();
                }
            }
        }
        ImGui.end();
        WINDOW.sync();
    }

    private static List<Path> replays() {
        long now = System.currentTimeMillis();
        if (now - lastScan < RESCAN_MILLIS) return replays;
        lastScan = now;
        Path folder = Flashback.getReplayFolder();
        if (!Files.isDirectory(folder)) return replays = List.of();
        try (Stream<Path> files = Files.walk(folder, 4)) {
            replays = files.filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(ClipsWindow::modified).reversed())
                    .toList();
        } catch (IOException exception) {
            replays = List.of();
        }
        return replays;
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0;
        }
    }

    private static String duration(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
