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
        if (state.segments() <= 0 || state.lineWidth() <= 0 || state.model() == null) {
            state = new ShapeState(state.shapeType(), state.shapeId(), state.x(), state.y(), state.z(),
                    state.pitch(), state.yaw(), state.roll(), state.scaleX(), state.scaleY(), state.scaleZ(),
                    state.sizeX(), state.sizeY(), state.sizeZ(),
                    state.segments() <= 0 ? 32 : state.segments(),
                    state.lineWidth() <= 0 ? 0.05f : state.lineWidth(),
                    state.color() == 0 ? 0xFFFFFFFF : state.color(),
                    state.points() == null ? java.util.List.of() : state.points(),
                    state.text(),
                    state.model() == null && state.shapeType().equals("obj")
                            ? "ryansrenderingkit:models/monkey.obj"
                            : state.model() == null ? "" : state.model(),
                    state.parentShapeId() == null ? "" : state.parentShapeId(),
                    state.seeThrough(), state.visible());
        }
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
