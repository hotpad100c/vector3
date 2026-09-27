package ml.mypals.vectorthree.flashback.pose;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/** The model part a Track Entity keyframe (and its change) anchors the camera on; null for the body part. */
public interface ModelPartHolder {
    String JSON_KEY = "vector3_model_part";

    @Nullable String vector3$modelPart();

    void vector3$setModelPart(@Nullable String part);

    static void write(Object keyframe, JsonObject json) {
        if (keyframe instanceof ModelPartHolder holder && holder.vector3$modelPart() != null) {
            json.addProperty(JSON_KEY, holder.vector3$modelPart());
        }
    }

    static void read(Object keyframe, JsonObject json) {
        if (keyframe instanceof ModelPartHolder holder && json.has(JSON_KEY)) holder.vector3$setModelPart(json.get(JSON_KEY).getAsString());
    }
}
