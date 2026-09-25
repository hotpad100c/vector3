package ml.mypals.vectorthree.flashback.custom;

import ml.mypals.vectorthree.prefab.PrefabGroups;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registers {@link CustomKeyframeType}s with Flashback and saves their keyframes as {type, value}. */
public final class CustomKeyframes implements JsonSerializer<CustomKeyframe<?>>, JsonDeserializer<CustomKeyframe<?>> {
    public static final CustomKeyframes SERIALIZER = new CustomKeyframes();
    private static final Map<String, CustomKeyframeType<?>> TYPES = new LinkedHashMap<>();

    private CustomKeyframes() {}

    public static void register(CustomKeyframeType<?> type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalStateException("Duplicate custom keyframe type " + type.id());
        }
        KeyframeRegistry.register(type);
    }

    public static @Nullable CustomKeyframeType<?> byId(String id) {
        return TYPES.get(id);
    }

    public static JsonObject write(CustomKeyframe<?> keyframe, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("type", keyframe.type().id());
        json.add(keyframe.type().valueField(), context.serialize(keyframe.value, keyframe.type().valueType()));
        json.add("interpolation_type", context.serialize(keyframe.interpolationType()));
        PrefabGroups.writeGroup(keyframe, json);
        return json;
    }

    public static <T> CustomKeyframe<T> read(CustomKeyframeType<T> type, JsonObject json, JsonDeserializationContext context) {
        T value = context.deserialize(json.get(type.valueField()), type.valueType());
        if (value == null) throw new JsonParseException("Missing value for " + type.id() + " keyframe");
        InterpolationType interpolation = json.has("interpolation_type")
                ? context.deserialize(json.get("interpolation_type"), InterpolationType.class)
                : InterpolationType.getDefault();
        CustomKeyframe<T> keyframe = type.newKeyframe(type.sanitize(value), interpolation);
        PrefabGroups.readGroup(keyframe, json);
        return keyframe;
    }

    @Override
    public JsonElement serialize(CustomKeyframe<?> keyframe, Type type, JsonSerializationContext context) {
        return write(keyframe, context);
    }

    @Override
    public CustomKeyframe<?> deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
        JsonObject json = element.getAsJsonObject();
        CustomKeyframeType<?> keyframeType = byId(json.get("type").getAsString());
        if (keyframeType == null) throw new JsonParseException("Unknown keyframe type " + json.get("type"));
        return read(keyframeType, json, context);
    }
}
