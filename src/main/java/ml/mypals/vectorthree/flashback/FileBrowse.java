package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.utils.AsyncFileDialogs;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** A browse button beside a path field, using Flashback's native file dialog. */
public final class FileBrowse {
    private static CompletableFuture<String> pending;
    private static String pendingId;

    private FileBrowse() {}

    /** Returns true on the frame the picked path is written into {@code value}. */
    public static boolean button(String id, ImString value, String description, String... extensions) {
        ImGui.sameLine();
        if (ImGui.button(I18n.get("vector3.file.browse") + "##browse_" + id) && pending == null && !AsyncFileDialogs.hasDialog()) {
            pending = AsyncFileDialogs.openFileDialog(startPath(value.get()), description, extensions);
            pendingId = id;
        }
        if (pending == null || !id.equals(pendingId) || !pending.isDone()) return false;
        CompletableFuture<String> done = pending;
        pending = null;
        String path;
        try {
            path = done.join();
        } catch (RuntimeException exception) {
            Vector3.LOGGER.warn("File dialog failed", exception);
            return false;
        }
        if (path == null || path.isBlank()) return false;
        value.set(path);
        return true;
    }

    private static String startPath(String current) {
        Path game = Minecraft.getInstance().gameDirectory.toPath();
        if (current == null || current.isBlank()) return game.toString();
        try {
            Path path = Path.of(current);
            if (!path.isAbsolute()) path = game.resolve(path);
            Path parent = path.toAbsolutePath().normalize().getParent();
            return parent != null && Files.isDirectory(parent) ? parent.toString() : game.toString();
        } catch (RuntimeException exception) {
            return game.toString();
        }
    }
}
