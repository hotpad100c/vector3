package ml.mypals.vectorthree.flashback.pose;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * The model part a Track Entity keyframe (and its change) anchors the camera on; null for the body part. With
 * {@code followPartRotation} the camera also turns with the part, its yaw / pitch / roll offsets relative to it.
 */
public interface ModelPartHolder {
    String JSON_KEY = "vector3_model_part";
    String FOLLOW_KEY = "vector3_follow_part_rotation";

    @Nullable String vector3$modelPart();

    void vector3$setModelPart(@Nullable String part);

    boolean vector3$followPartRotation();

    void vector3$setFollowPartRotation(boolean follow);

    static void copy(Object from, Object to) {
        if (from instanceof ModelPartHolder source && to instanceof ModelPartHolder target) {
            target.vector3$setModelPart(source.vector3$modelPart());
            target.vector3$setFollowPartRotation(source.vector3$followPartRotation());
        }
    }

    static void write(Object keyframe, JsonObject json) {
        if (keyframe instanceof ModelPartHolder holder && holder.vector3$modelPart() != null) {
            json.addProperty(JSON_KEY, holder.vector3$modelPart());
            if (holder.vector3$followPartRotation()) json.addProperty(FOLLOW_KEY, true);
        }
    }

    static void read(Object keyframe, JsonObject json) {
        if (!(keyframe instanceof ModelPartHolder holder)) return;
        if (json.has(JSON_KEY)) holder.vector3$setModelPart(json.get(JSON_KEY).getAsString());
        if (json.has(FOLLOW_KEY)) holder.vector3$setFollowPartRotation(json.get(FOLLOW_KEY).getAsBoolean());
    }
}
