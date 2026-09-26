package ml.mypals.vectorthree.multiedit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PropertyClipboard {
    public record Clip(String key, MultiEditSession.Kind kind, Object value) {
        MultiEditSession.Change change() {
            return new MultiEditSession.Change(key, value, null, kind == MultiEditSession.Kind.COMBO ? (String) value : null);
        }
    }

    private static final Gson GSON = new Gson();
    private static @Nullable List<Clip> clips;

    private PropertyClipboard() {}

    public static List<Clip> get() {
        if (clips == null) clips = load();
        return clips;
    }

    public static void set(List<Clip> copied) {
        clips = List.copyOf(copied);
        save(clips);
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/vector3/property_clipboard.json");
    }

    private static void save(List<Clip> copied) {
        JsonArray array = new JsonArray();
        for (Clip clip : copied) {
            JsonObject json = new JsonObject();
            json.addProperty("key", clip.key());
            json.addProperty("kind", clip.kind().name());
            switch (clip.value()) {
                case float[] floats -> json.add("floats", GSON.toJsonTree(floats));
                case int[] ints -> json.add("ints", GSON.toJsonTree(ints));
                case Boolean value -> json.addProperty("boolean", value);
                case Integer value -> json.addProperty("int", value);
                case Float value -> json.addProperty("float", value);
                case Double value -> json.addProperty("double", value);
                case String value -> json.addProperty("string", value);
                default -> {
                    continue;
                }
            }
            array.add(json);
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(array));
        } catch (IOException exception) {
            Vector3.LOGGER.warn("Could not save the Properties clipboard", exception);
        }
    }

    private static List<Clip> load() {
        List<Clip> loaded = new ArrayList<>();
        if (!Files.isRegularFile(file())) return loaded;
        try {
            for (JsonElement element : JsonParser.parseString(Files.readString(file())).getAsJsonArray()) {
                JsonObject json = element.getAsJsonObject();
                Object value = json.has("floats") ? GSON.fromJson(json.get("floats"), float[].class)
                        : json.has("ints") ? GSON.fromJson(json.get("ints"), int[].class)
                        : json.has("boolean") ? (Object) json.get("boolean").getAsBoolean()
                        : json.has("int") ? (Object) json.get("int").getAsInt()
                        : json.has("float") ? (Object) json.get("float").getAsFloat()
                        : json.has("double") ? (Object) json.get("double").getAsDouble()
                        : json.has("string") ? json.get("string").getAsString() : null;
                if (value == null) continue;
                loaded.add(new Clip(json.get("key").getAsString(),
                        MultiEditSession.Kind.valueOf(json.get("kind").getAsString()), value));
            }
        } catch (IOException | RuntimeException exception) {
            Vector3.LOGGER.warn("Could not read the Properties clipboard", exception);
        }
        return loaded;
    }
}
