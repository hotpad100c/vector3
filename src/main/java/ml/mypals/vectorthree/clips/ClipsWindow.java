package ml.mypals.vectorthree.clips;

import imgui.moulberry90.flag.ImGuiWindowFlags;
import com.moulberry.flashback.Flashback;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.ImDrawList;
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

    private static final int PROGRESS_FLAGS = ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.AlwaysAutoResize;

    /** A fixed window in the middle of the screen while clips compose in the background. */
    public static void renderProgress() {
        if (!ClipProject.isComposing()) return;
        ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() / 2, ImGui.getIO().getDisplaySizeY() / 2,
                ImGuiCond.Always, 0.5f, 0.5f);
        if (ImGui.begin(I18n.get("vector3.clips.composing") + "###vector3_clip_progress", PROGRESS_FLAGS)) {
            double progress = ClipProject.progress();
            ImGui.progressBar((float) progress, 320, 0, Math.round(progress * 100) + "%");
            ImGui.textDisabled(I18n.get("vector3.clips.composing_hint"));
        }
        ImGui.end();
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
            float spacing = ImGui.getStyle().getItemSpacingX();
            int columns = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + spacing) / (CARD_WIDTH + spacing)));
            int column = 0;
            for (Path replay : replays()) {
                ReplayArchive.Info info = ReplayArchive.read(replay);
                if (info == null) continue;
                if (column++ % columns != 0) ImGui.sameLine();
                card(replay, info);
            }
        }
        ImGui.end();
        WINDOW.sync();
    }

    private static final float CARD_WIDTH = 150, IMAGE_HEIGHT = 84;

    // A cover above the name and length; the whole card is the drag source.
    private static void card(Path replay, ReplayArchive.Info info) {
        String name = info.meta().name == null || info.meta().name.isBlank()
                ? replay.getFileName().toString() : info.meta().name;
        float lineHeight = ImGui.getTextLineHeight();
        float height = IMAGE_HEIGHT + lineHeight * 2 + 8;
        float left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##clip_card_" + replay, CARD_WIDTH, height);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList draw = ImGui.getWindowDrawList();
        draw.addRectFilled(left, top, left + CARD_WIDTH, top + height,
                ImGui.getColorU32(hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg), 4);
        ClipCovers.Cover cover = ClipCovers.of(replay.toString());
        if (cover != null) {
            float width = Math.min(CARD_WIDTH - 8, (IMAGE_HEIGHT) * cover.aspect());
            float imageLeft = left + (CARD_WIDTH - width) / 2;
            draw.addImage(cover.textureId(), imageLeft, top + 4, imageLeft + width, top + 4 + IMAGE_HEIGHT);
        } else {
            draw.addRectFilled(left + 4, top + 4, left + CARD_WIDTH - 4, top + 4 + IMAGE_HEIGHT, 0xFF202020, 3);
        }
        draw.pushClipRect(left + 4, top, left + CARD_WIDTH - 4, top + height, true);
        draw.addText(left + 6, top + IMAGE_HEIGHT + 6, ImGui.getColorU32(ImGuiCol.Text), name);
        draw.addText(left + 6, top + IMAGE_HEIGHT + 6 + lineHeight, ImGui.getColorU32(ImGuiCol.TextDisabled),
                duration(info.totalTicks()));
        draw.popClipRect();
        if (hovered) ImGui.setTooltip(name + "\n" + replay);
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD, replay.toString());
            ImGui.text(name);
            ImGui.endDragDropSource();
        }
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
