package ml.mypals.vectorthree;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the plug/socket layering: {@code core} knows neither Flashback nor Minecraft's unstable client internals, {@code mc} knows no
 * Flashback, {@code fb} reaches Minecraft rendering only through {@code mc}. See "Architecture" in HANDOFF.md.
 */
class ArchitectureTest {
    private static final String BASE = "ml.mypals.vectorthree.";
    private static final Path SOURCES = Path.of("src/main/java/ml/mypals/vectorthree");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?;", Pattern.MULTILINE);

    private static final Pattern FLASHBACK = Pattern.compile("^(com\\.moulberry|imgui)\\..*");
    private static final Pattern MC_INTERNALS = Pattern.compile("^(net\\.minecraft\\.client\\.(renderer|model|particle|gui|resources\\.model|sounds)"
            + "|com\\.mojang\\.blaze3d\\.(pipeline|vertex|buffers|systems|shaders|opengl|shader)|net\\.caffeinemc|net\\.irisshaders)\\..*");
    private static final Pattern MC_RENDERING = Pattern.compile("^(net\\.minecraft\\.client\\.(renderer|model|particle|resources\\.model|sounds)"
            + "|com\\.mojang\\.blaze3d\\.(pipeline|vertex|buffers|systems|shaders|opengl|shader)|net\\.caffeinemc|net\\.irisshaders)\\..*");
    private static final Pattern RENDER_KIT = Pattern.compile("^(ml\\.mypals\\.ryansrenderingkit|com\\.mojang\\.renderpearl)\\..*");
    private static final Pattern MIXIN_API = Pattern.compile("^(org\\.spongepowered|com\\.llamalad7)\\..*");

    private enum Zone {
        ROOT(Set.of("core", "fb", "mc", "mixin.flashback", "mixin.minecraft", "mixin.rrk", "root")),
        CORE(Set.of("core")),
        MC(Set.of("core", "mc", "mixin.minecraft")),
        FB(Set.of("core", "mc", "fb", "mixin.flashback", "mixin.minecraft")),
        MIXIN_MC(Set.of("core", "mc", "mixin.minecraft", "mixin.rrk")),
        MIXIN_FB(Set.of("core", "mc", "fb", "mixin.flashback", "mixin.minecraft"));

        final Set<String> mayUse;

        Zone(Set<String> mayUse) {
            this.mayUse = mayUse;
        }
    }

    @Test
    void layersOnlyDependDownward() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SOURCES)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCES.relativize(path).toString().replace('\\', '/');
                Zone zone = zoneOf(relative);
                Matcher matcher = IMPORT.matcher(Files.readString(path));
                while (matcher.find()) check(relative, zone, matcher.group(1), violations);
            }
        }
        assertTrue(violations.isEmpty(), () -> violations.size() + " layering violations:\n" + String.join("\n", violations));
    }

    private static Zone zoneOf(String relative) {
        if (relative.startsWith("core/")) return Zone.CORE;
        if (relative.startsWith("mc/")) return Zone.MC;
        if (relative.startsWith("fb/")) return Zone.FB;
        if (relative.startsWith("mixin/flashback/")) return Zone.MIXIN_FB;
        if (relative.startsWith("mixin/")) return Zone.MIXIN_MC;
        return Zone.ROOT;
    }

    private static String layerOf(String projectPath) {
        if (projectPath.startsWith("mixin.flashback.")) return "mixin.flashback";
        if (projectPath.startsWith("mixin.minecraft.")) return "mixin.minecraft";
        if (projectPath.startsWith("mixin.rrk.")) return "mixin.rrk";
        if (projectPath.startsWith("mixin.")) return "mixin.minecraft";
        int dot = projectPath.indexOf('.');
        String head = dot < 0 ? "root" : projectPath.substring(0, dot);
        return head.equals("core") || head.equals("fb") || head.equals("mc") ? head : "root";
    }

    private static void check(String file, Zone zone, String imported, List<String> violations) {
        if (imported.startsWith(BASE)) {
            String layer = layerOf(imported.substring(BASE.length()));
            if (!zone.mayUse.contains(layer)) violations.add(file + " -> " + layer + " (" + imported + ")");
            return;
        }
        boolean flashback = FLASHBACK.matcher(imported).matches();
        boolean internals = MC_INTERNALS.matcher(imported).matches();
        boolean mixinApi = MIXIN_API.matcher(imported).matches();
        switch (zone) {
            case CORE -> {
                if (flashback || internals || mixinApi || RENDER_KIT.matcher(imported).matches()) violations.add(file + " uses " + imported + " (core must stay portable)");
            }
            case MC, MIXIN_MC -> {
                if (flashback) violations.add(file + " uses " + imported + " (Minecraft side must not know Flashback)");
                if (mixinApi && zone == Zone.MC) violations.add(file + " uses " + imported + " (mixin annotations belong in mixin/)");
            }
            case FB -> {
                if (MC_RENDERING.matcher(imported).matches()) violations.add(file + " uses " + imported + " (reach Minecraft rendering through mc/)");
                if (mixinApi) violations.add(file + " uses " + imported + " (mixin annotations belong in mixin/)");
            }
            default -> { }
        }
    }
}
