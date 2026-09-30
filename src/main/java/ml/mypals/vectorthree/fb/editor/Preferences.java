package ml.mypals.vectorthree.fb.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ml.mypals.vectorthree.core.Mod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Editor switches that belong to the user rather than to a replay, kept in {@code config/vector3/preferences.json}. */
public final class Preferences {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vector3").resolve("preferences.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Boolean> VALUES = load();

    private Preferences() {}

    public static boolean get(String key, boolean fallback) {
        return VALUES.getOrDefault(key, fallback);
    }

    public static void set(String key, boolean value) {
        if (Boolean.valueOf(value).equals(VALUES.put(key, value))) return;
        save();
    }

    private static Map<String, Boolean> load() {
        Map<String, Boolean> values = new TreeMap<>();
        if (!Files.isRegularFile(FILE)) return values;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (root.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean())
                        values.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
        } catch (IOException | RuntimeException exception) {
            Mod.LOGGER.warn("Could not read {}, using defaults", FILE, exception);
        }
        return values;
    }

    private static void save() {
        JsonObject json = new JsonObject();
        VALUES.forEach(json::addProperty);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Mod.LOGGER.warn("Could not save {}", FILE, exception);
        }
    }
}
