package ml.mypals.vectorthree.clips;

import com.moulberry.flashback.io.ReplayCombiner;
import net.minecraft.core.RegistryAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleConsumer;

/**
 * Builds a project's working replay from its clips: each clip is cut down to the Flashback chunks it touches, and
 * the cut archives are joined end to end with Flashback's own ReplayCombiner, which remaps the chunk caches.
 */
public final class ClipComposer {
    private ClipComposer() {}

    /** Where each clip lands, worked out from the archives' metadata alone. */
    public static List<ClipRef> layout(List<ClipRef> clips) throws IOException {
        List<ClipRef> placed = new ArrayList<>(clips.size());
        int position = 0;
        for (ClipRef clip : clips) {
            ReplayArchive.Info info = ReplayArchive.read(Path.of(clip.source()));
            if (info == null) throw new IOException("Cannot read clip source " + clip.source());
            int in = Math.clamp(clip.in(), 0, info.totalTicks()), out = Math.clamp(clip.out(), in, info.totalTicks());
            // Composing cuts the chunks to exactly in..out, so nothing of the clip is left to skip.
            int length = Math.max(1, out - in);
            placed.add(clip.withRange(in, in + length).placed(position, in, length));
            position += length;
        }
        return placed;
    }

    /** Writes the working replay for {@code clips} (already laid out) to {@code output}, keeping {@code id}. */
    public static void compose(List<ClipRef> clips, UUID id, String name, Path output, RegistryAccess registries,
            DoubleConsumer progress) throws Exception {
        Path work = Files.createTempDirectory(output.getParent(), ".vector3_compose");
        int steps = clips.size() * 2 - 1, done = 0;
        try {
            Path joined = null;
            for (int i = 0; i < clips.size(); i++) {
                ClipRef clip = clips.get(i);
                Path source = Path.of(clip.source());
                ReplayArchive.Info info = ReplayArchive.read(source);
                if (info == null) throw new IOException("Cannot read clip source " + clip.source());
                Path cut = work.resolve("clip" + i + ".zip");
                ReplayArchive.writeRange(source, ReplayArchive.span(info, clip.in(), clip.out()), clip.in(), clip.out(), cut);
                progress.accept(++done / (double) steps);
                if (joined == null) {
                    joined = cut;
                } else {
                    Path next = work.resolve("joined" + i + ".zip");
                    ReplayCombiner.combine(registries, name, joined, cut, next);
                    joined = next;
                    progress.accept(++done / (double) steps);
                }
            }
            if (joined == null) throw new IOException("A project needs at least one clip");
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
