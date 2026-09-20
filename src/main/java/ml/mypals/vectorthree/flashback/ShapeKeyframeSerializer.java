package ml.mypals.vectorthree.flashback;

import com.google.gson.*;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import ml.mypals.vectorthree.shape.ShapeState;

import java.lang.reflect.Type;

public final class ShapeKeyframeSerializer implements JsonSerializer<ShapeKeyframe>, JsonDeserializer<ShapeKeyframe> {
    public static final ShapeKeyframeSerializer INSTANCE = new ShapeKeyframeSerializer();
    private ShapeKeyframeSerializer() {}

    public static JsonObject write(ShapeKeyframe keyframe, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("type", ShapeKeyframeType.ID);
        json.add("shape", context.serialize(keyframe.state));
        json.add("interpolation_type", context.serialize(keyframe.interpolationType()));
        return json;
    }

    public static ShapeKeyframe read(JsonObject json, JsonDeserializationContext context) {
        ShapeState state = context.deserialize(json.get("shape"), ShapeState.class);
        InterpolationType interpolation = json.has("interpolation_type")
                ? context.deserialize(json.get("interpolation_type"), InterpolationType.class)
                : InterpolationType.getDefault();
        return new ShapeKeyframe(state, interpolation);
    }

    @Override public JsonElement serialize(ShapeKeyframe value, Type type, JsonSerializationContext context) {
        return write(value, context);
    }

    @Override public ShapeKeyframe deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
        return read(json.getAsJsonObject(), context);
    }
}
