package ml.mypals.vectorthree.prefab;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import ml.mypals.vectorthree.Vector3;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The groups of one scene. A concrete type with its own adapter: the field Mixin adds to EditorScene
 * loses its generic signature, so Gson's reflection would read a Map's values back as plain JSON maps.
 * The JSON keeps the shape that reflection wrote, so older saves still load.
 */
public final class PrefabGroupStore {
    public static final Adapter ADAPTER = new Adapter();
    final Map<String, PrefabGroup> groups = new LinkedHashMap<>();

    public static final class Adapter implements JsonSerializer<PrefabGroupStore>, JsonDeserializer<PrefabGroupStore> {
        @Override
        public JsonElement serialize(PrefabGroupStore store, Type type, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            store.groups.forEach((id, group) -> json.add(id, write(group, context)));
            return json;
        }

        @Override
        public PrefabGroupStore deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            PrefabGroupStore store = new PrefabGroupStore();
            if (!json.isJsonObject()) return store;
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                try {
                    store.groups.put(entry.getKey(), read(entry.getKey(), entry.getValue().getAsJsonObject(), context));
                } catch (Exception exception) {
                    Vector3.LOGGER.warn("Dropping unreadable prefab group {}", entry.getKey(), exception);
                }
            }
            return store;
        }
    }

    private static JsonObject write(PrefabGroup group, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("id", group.id());
        json.addProperty("name", group.name());
        json.add("prefab", group.prefab() == null ? JsonNull.INSTANCE : writePrefab(group.prefab(), context));
        if (group.transform() != null) {
            JsonObject transform = new JsonObject();
            transform.add("center", context.serialize(group.transform().center(), Vector3d.class));
            transform.add("rotationDegrees", context.serialize(group.transform().rotationDegrees(), Vector3f.class));
            transform.addProperty("scale", group.transform().scale());
            json.add("transform", transform);
        }
        json.addProperty("timeScale", group.timeScale());
        json.addProperty("startTick", group.startTick());
        return json;
    }

    private static JsonObject writePrefab(Prefab prefab, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("name", prefab.name());
        com.google.gson.JsonArray tracks = new com.google.gson.JsonArray();
        for (Prefab.Track track : prefab.tracks()) {
            JsonObject trackJson = new JsonObject();
            trackJson.add("type", context.serialize(track.type(), KeyframeType.class));
            if (track.customName() != null) trackJson.addProperty("customName", track.customName());
            trackJson.addProperty("customColour", track.customColour());
            JsonObject keyframes = new JsonObject();
            track.keyframes().forEach((tick, keyframe) -> keyframes.add(String.valueOf(tick), context.serialize(keyframe, Keyframe.class)));
            trackJson.add("keyframes", keyframes);
            tracks.add(trackJson);
        }
        json.add("tracks", tracks);
        return json;
    }

    private static PrefabGroup read(String id, JsonObject json, JsonDeserializationContext context) {
        Prefab prefab = json.has("prefab") && json.get("prefab").isJsonObject() ? readPrefab(json.getAsJsonObject("prefab"), context) : null;
        PrefabTransform transform = null;
        if (json.has("transform") && json.get("transform").isJsonObject()) {
            JsonObject t = json.getAsJsonObject("transform");
            transform = new PrefabTransform(context.deserialize(t.get("center"), Vector3d.class),
                    context.deserialize(t.get("rotationDegrees"), Vector3f.class), t.get("scale").getAsDouble());
        }
        return new PrefabGroup(json.has("id") ? json.get("id").getAsString() : id, json.get("name").getAsString(), prefab,
                transform, json.has("timeScale") ? json.get("timeScale").getAsFloat() : 1,
                json.has("startTick") ? json.get("startTick").getAsInt() : 0);
    }

    private static Prefab readPrefab(JsonObject json, JsonDeserializationContext context) {
        List<Prefab.Track> tracks = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("tracks")) {
            JsonObject track = element.getAsJsonObject();
            KeyframeType<?> type = context.deserialize(track.get("type"), KeyframeType.class);
            if (type == null) continue;
            TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : track.getAsJsonObject("keyframes").entrySet()) {
                keyframes.put(Integer.parseInt(entry.getKey()), context.deserialize(entry.getValue(), Keyframe.class));
            }
            tracks.add(new Prefab.Track(type, track.has("customName") ? track.get("customName").getAsString() : null,
                    track.has("customColour") ? track.get("customColour").getAsInt() : 0, keyframes));
        }
        return new Prefab(json.get("name").getAsString(), tracks);
    }
}
