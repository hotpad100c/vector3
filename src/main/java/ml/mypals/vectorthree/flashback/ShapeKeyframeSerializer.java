package ml.mypals.vectorthree.flashback;

import com.google.gson.*;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.text.TextSettings;

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
        if (state.segments() <= 0 || state.lineWidth() <= 0 || state.model() == null
                || state.blockProperties() == null
                || state.shapeType().equals("text") && (state.text() == null || state.text().font() == null)) {
            TextSettings text = state.text();
            if (state.shapeType().equals("text")) {
                if (text == null) text = TextSettings.defaults();
                else if (text.font() == null) text = new TextSettings(text.value(), text.holdText(),
                        text.shadow(), text.outline(), text.billboard(), "minecraft:default");
            }
            state = new ShapeState(state.shapeType(), state.shapeId(), state.x(), state.y(), state.z(),
                    state.pitch(), state.yaw(), state.roll(), state.scaleX(), state.scaleY(), state.scaleZ(),
                    state.sizeX(), state.sizeY(), state.sizeZ(),
                    state.segments() <= 0 ? 32 : state.segments(),
                    state.lineWidth() <= 0 ? 0.05f : state.lineWidth(),
                    state.color() == 0 ? 0xFFFFFFFF : state.color(),
                    state.points() == null ? java.util.List.of() : state.points(),
                    text,
                    state.model() != null ? state.model() : switch (state.shapeType()) {
                        case "obj" -> "ryansrenderingkit:models/monkey.obj";
                        case "block" -> "minecraft:stone";
                        case "item" -> "minecraft:diamond";
                        case "entity" -> "minecraft:pig";
                        default -> "";
                    },
                    state.blockProperties() == null ? java.util.Map.of() : state.blockProperties(),
                    state.parentShapeId() == null ? "" : state.parentShapeId(),
                    state.seeThrough(), state.visible(), state.outline(),
                    state.outlineColor() == 0 ? 0xFFFFFFFF : state.outlineColor(),
                    state.videoStartTick(), state.playAudio(),
                    state.manualPlayback(), state.noLoop(), state.playbackSeconds(), state.name(), state.wireframe(), state.areaOptions(), state.bypassShaders());
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
