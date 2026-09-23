package ml.mypals.vectorthree.text;

import ml.mypals.vectorthree.Vector3;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Font choices for the text shape editor: resource-pack font definitions (rendered by vanilla),
 * .ttf/.otf files inside resource packs, and font files in the game directory's {@code fonts/} folder
 * (both rendered by {@link SdfFont}). Cached briefly so an open dropdown doesn't rescan every frame.
 */
public final class FontOptions {
    private static final String FONT_FOLDER = "fonts";
    private static final long REFRESH_MILLIS = 3000;
    public record Option(String spec, boolean sdf) {}

    private static List<Option> cached = List.of();
    private static long lastRefresh;

    private FontOptions() {}

    public static List<Option> list() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh > REFRESH_MILLIS) {
            cached = scan();
            lastRefresh = now;
        }
        return cached;
    }

    private static List<Option> scan() {
        return specs().stream().map(spec -> new Option(spec, SdfFont.rendersAsSdf(spec))).toList();
    }

    private static List<String> specs() {
        List<String> fonts = new ArrayList<>();
        var resources = Minecraft.getInstance().getResourceManager();
        List<String> definitions = new ArrayList<>();
        List<String> packFiles = new ArrayList<>();
        for (Identifier id : resources.listResources("font", FontOptions::isListed).keySet()) {
            String path = id.getPath();
            if (path.endsWith(".json")) {
                // font/include/* are fragments other definitions pull in, not fonts of their own.
                if (path.startsWith("font/include/")) continue;
                definitions.add(id.getNamespace() + ":" + path.substring("font/".length(), path.length() - ".json".length()));
            } else {
                packFiles.add(id.toString());
            }
        }
        definitions.sort(null);
        packFiles.sort(null);
        fonts.addAll(definitions);
        fonts.addAll(packFiles);
        fonts.addAll(folderFiles());
        return List.copyOf(fonts);
    }

    private static boolean isListed(Identifier id) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".json") || isFontFile(path);
    }

    private static boolean isFontFile(String name) {
        return name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc");
    }

    /** Creates the game directory's {@code fonts/} folder so players have an obvious place to drop fonts. */
    public static void ensureFontFolder() {
        try {
            Files.createDirectories(FabricLoader.getInstance().getGameDir().resolve(FONT_FOLDER));
        } catch (IOException exception) {
            Vector3.LOGGER.warn("Could not create the {} folder", FONT_FOLDER, exception);
        }
    }

    private static List<String> folderFiles() {
        Path folder = FabricLoader.getInstance().getGameDir().resolve(FONT_FOLDER);
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> Files.isRegularFile(file)
                            && isFontFile(file.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(file -> FONT_FOLDER + "/" + file.getFileName())
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }
}
