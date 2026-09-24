package ml.mypals.vectorthree.prefab;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import ml.mypals.vectorthree.Vector3;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class PrefabStore {
    public record Saved(Path file, Prefab prefab) {}

    private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve("vector3").resolve("prefabs");

    private PrefabStore() {}

    public static List<Saved> list() {
        List<Saved> saved = new ArrayList<>();
        if (!Files.isDirectory(FOLDER)) return saved;
        try (Stream<Path> files = Files.list(FOLDER)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                try {
                    saved.add(new Saved(file, read(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)))));
                } catch (Exception exception) {
                    Vector3.LOGGER.warn("Could not read prefab {}", file, exception);
                }
            }
        } catch (IOException exception) {
            Vector3.LOGGER.warn("Could not list prefabs in {}", FOLDER, exception);
        }
        return saved;
    }

    public static Path save(Prefab prefab) throws IOException {
        Files.createDirectories(FOLDER);
        String base = prefab.name().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        Path file = FOLDER.resolve((base.isEmpty() ? "prefab" : base) + ".json");
        Files.writeString(file, FlashbackGson.PRETTY.toJson(write(prefab)), StandardCharsets.UTF_8);
        return file;
    }

    public static void delete(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    private static JsonObject write(Prefab prefab) {
        Gson gson = FlashbackGson.COMPRESSED;
        JsonArray tracks = new JsonArray();
        for (Prefab.Track track : prefab.tracks()) {
            JsonObject json = new JsonObject();
            json.add("type", gson.toJsonTree(track.type(), KeyframeType.class));
            if (track.customName() != null) json.addProperty("name", track.customName());
            json.addProperty("colour", track.customColour());
            JsonObject keyframes = new JsonObject();
            track.keyframes().forEach((tick, keyframe) -> keyframes.add(String.valueOf(tick), gson.toJsonTree(keyframe, Keyframe.class)));
            json.add("keyframes", keyframes);
            tracks.add(json);
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", prefab.name());
        json.add("tracks", tracks);
        return json;
    }

    private static Prefab read(JsonElement element) {
        Gson gson = FlashbackGson.COMPRESSED;
        JsonObject json = element.getAsJsonObject();
        List<Prefab.Track> tracks = new ArrayList<>();
        for (JsonElement trackElement : json.getAsJsonArray("tracks")) {
            JsonObject track = trackElement.getAsJsonObject();
            KeyframeType<?> type = gson.fromJson(track.get("type"), KeyframeType.class);
            if (type == null) continue;
            TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : track.getAsJsonObject("keyframes").entrySet()) {
                keyframes.put(Integer.parseInt(entry.getKey()), gson.fromJson(entry.getValue(), Keyframe.class));
            }
            tracks.add(new Prefab.Track(type, track.has("name") ? track.get("name").getAsString() : null,
                    track.has("colour") ? track.get("colour").getAsInt() : 0, keyframes));
        }
        return new Prefab(json.get("name").getAsString(), tracks);
    }
}
