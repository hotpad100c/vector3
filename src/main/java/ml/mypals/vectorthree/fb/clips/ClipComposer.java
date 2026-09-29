package ml.mypals.vectorthree.fb.clips;

import com.moulberry.flashback.io.ReplayCombiner;
import net.minecraft.core.RegistryAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

/**
 * Grows a project's working replay: each new source is appended whole, with Flashback's own ReplayCombiner (which
 * remaps the chunk caches). What is already in the working replay is never rebuilt, and nothing is cut, so clips can
 * later be trimmed back out to their full source without composing again.
 */
public final class ClipComposer {
    private ClipComposer() {}

    /** Where each new source starts in the grown replay, in the order they are appended. */
    public static Map<String, Integer> layout(int workingTicks, List<String> sources) throws IOException {
        Map<String, Integer> starts = new LinkedHashMap<>();
        int position = workingTicks;
        for (String source : sources) {
            if (starts.containsKey(source)) continue;
            ReplayArchive.Info info = ReplayArchive.read(Path.of(source));
            if (info == null) throw new IOException("Cannot read clip source " + source);
            starts.put(source, position);
            position += Math.max(1, info.totalTicks());
        }
        return starts;
    }

    /** Writes {@code working} followed by {@code sources} to {@code output}, keeping {@code id}. */
    public static void compose(Path working, List<String> sources, UUID id, String name, Path output,
            RegistryAccess registries, DoubleConsumer progress) throws Exception {
        Path work = Files.createTempDirectory(output.getParent(), ".vector3_compose");
        List<String> unique = new ArrayList<>(new java.util.LinkedHashSet<>(sources));
        try {
            Path joined = working;
            for (int i = 0; i < unique.size(); i++) {
                Path next = work.resolve("joined" + i + ".zip");
                ReplayCombiner.combine(registries, name, joined, Path.of(unique.get(i)), next);
                joined = next;
                progress.accept((i + 1) / (double) unique.size());
            }
            if (joined == working) throw new IOException("Nothing to append");
            ReplayArchive.editMeta(joined, meta -> {
                meta.replayIdentifier = id;
                meta.name = name;
            });
            Files.move(joined, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try (var files = Files.walk(work)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
