package ml.mypals.vectorthree.clips;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import com.moulberry.flashback.record.FlashbackMeta;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Reads Flashback replay archives (zip files holding metadata.json, chunk files and level chunk caches). */
public final class ReplayArchive {
    public record Chunk(String name, int start, int duration) {
        int end() {
            return start + duration;
        }
    }

    public record Info(FlashbackMeta meta, List<Chunk> chunks) {
        public int totalTicks() {
            return chunks.isEmpty() ? meta.totalTicks : chunks.getLast().end();
        }
    }

    private record Cached(long modified, Info info) {}

    private static final Gson GSON = new Gson();
    private static final Map<Path, Cached> CACHE = new ConcurrentHashMap<>();

    private ReplayArchive() {}

    public static @Nullable Info read(Path archive) {
        try {
            long modified = Files.getLastModifiedTime(archive).toMillis();
            Cached cached = CACHE.get(archive);
            if (cached != null && cached.modified() == modified) return cached.info();
            try (FileSystem zip = FileSystems.newFileSystem(archive)) {
                Info info = info(readMeta(zip));
                CACHE.put(archive, new Cached(modified, info));
                return info;
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    static FlashbackMeta readMeta(FileSystem zip) throws IOException {
        String json = Files.readString(zip.getPath("/metadata.json"));
        return FlashbackMeta.fromJson(GSON.fromJson(json, JsonObject.class));
    }

    static Info info(FlashbackMeta meta) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        for (Map.Entry<String, FlashbackChunkMeta> entry : meta.chunks.entrySet()) {
            chunks.add(new Chunk(entry.getKey(), start, entry.getValue().duration));
            start += entry.getValue().duration;
        }
        return new Info(meta, chunks);
    }

    /** The chunks a clip from {@code in} to {@code out} needs; never empty for a non-empty replay. */
    static List<Chunk> span(Info info, int in, int out) {
        List<Chunk> span = new ArrayList<>();
        for (Chunk chunk : info.chunks()) {
            if (chunk.end() > in && chunk.start() < Math.max(out, in + 1)) span.add(chunk);
        }
        if (span.isEmpty() && !info.chunks().isEmpty()) span.add(info.chunks().getLast());
        return span;
    }

    /**
     * Writes a copy of {@code source} holding only {@code keep}'s chunks. Everything else in the archive (level
     * chunk caches, icon) is copied as is, which ReplayCombiner then remaps when it joins archives.
     */
    /** Like {@link #writeSubset(Path, List, Path)}, but the first and last chunks are cut to exactly in..out. */
    static void writeRange(Path source, List<Chunk> keep, int in, int out, Path target) throws IOException {
        writeSubset(source, keep, target);
        try (FileSystem zip = FileSystems.newFileSystem(target)) {
            FlashbackMeta meta = readMeta(zip);
            int total = 0;
            for (Chunk chunk : keep) {
                int drop = Math.max(0, in - chunk.start());
                int end = Math.min(chunk.end(), out);
                int length = Math.max(1, end - chunk.start() - drop);
                if (drop > 0 || end < chunk.end()) {
                    Path file = zip.getPath("/" + chunk.name());
                    Files.write(file, ChunkCutter.cut(Files.readAllBytes(file), drop, length));
                    meta.chunks.get(chunk.name()).duration = length;
                }
                total += meta.chunks.get(chunk.name()).duration;
            }
            meta.totalTicks = total;
            Files.writeString(zip.getPath("/metadata.json"), GSON.toJson(meta.toJson()), StandardCharsets.UTF_8);
        }
    }

    static void writeSubset(Path source, List<Chunk> keep, Path target) throws IOException {
        Files.deleteIfExists(target);
        try (FileSystem in = FileSystems.newFileSystem(source);
             FileSystem out = FileSystems.newFileSystem(target, Map.of("create", "true"))) {
            FlashbackMeta meta = readMeta(in);
            LinkedHashMap<String, FlashbackChunkMeta> chunks = new LinkedHashMap<>();
            int total = 0;
            for (Chunk chunk : keep) {
                FlashbackChunkMeta chunkMeta = meta.chunks.get(chunk.name());
                chunks.put(chunk.name(), chunkMeta);
                total += chunk.duration();
            }
            // A subset may start mid-recording, so its first chunk has to set the world up from its snapshot.
            if (!chunks.isEmpty()) chunks.firstEntry().getValue().forcePlaySnapshot = true;
            meta.chunks = chunks;
            meta.totalTicks = total;
            meta.replayMarkers = new TreeMap<>();
            java.util.Set<String> skipped = new java.util.HashSet<>(readMeta(in).chunks.keySet());
            skipped.removeAll(chunks.keySet());
            try (Stream<Path> files = Files.walk(in.getPath("/"))) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (Files.isDirectory(file)) continue;
                    String name = file.toString();
                    if (name.equals("/metadata.json") || skipped.contains(name.substring(1))) continue;
                    Path copy = out.getPath(name);
                    if (copy.getParent() != null) Files.createDirectories(copy.getParent());
                    Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(out.getPath("/metadata.json"), GSON.toJson(meta.toJson()), StandardCharsets.UTF_8);
        }
    }

    /** Rewrites an archive's metadata in place. */
    static void editMeta(Path archive, java.util.function.Consumer<FlashbackMeta> edit) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(archive)) {
            FlashbackMeta meta = readMeta(zip);
            edit.accept(meta);
            Files.writeString(zip.getPath("/metadata.json"), GSON.toJson(meta.toJson()), StandardCharsets.UTF_8);
        }
        CACHE.remove(archive);
    }
}
