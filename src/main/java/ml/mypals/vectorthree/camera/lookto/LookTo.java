package ml.mypals.vectorthree.camera.lookto;

import com.moulberry.flashback.combo_options.ComboOption;
import com.moulberry.flashback.combo_options.TrackingBodyPart;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * One LookTo keyframe. From a keyframe that doesn't end the scope until the next keyframe, the camera
 * looks at a point blended from this keyframe's target toward the next one's; an ending keyframe
 * only supplies that final target.
 */
public record LookTo(boolean endsScope, Kind kind, @Nullable UUID entity, TrackingBodyPart bodyPart,
                     double x, double y, double z, @Nullable String shapeId) {

    public enum Kind implements ComboOption {
        ENTITY("vector3.look_to.kind.entity"),
        POSITION("vector3.look_to.kind.position"),
        SHAPE("vector3.look_to.kind.shape");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        @Override
        public String text() {
            return I18n.get(key);
        }
    }

    public static LookTo at(Vec3 position) {
        return new LookTo(false, Kind.POSITION, null, TrackingBodyPart.HEAD, position.x, position.y, position.z, null);
    }

    /** The point to look at, or null when the entity or shape isn't loaded. */
    public @Nullable Vec3 resolve(float partialTick) {
        return switch (kind) {
            case POSITION -> new Vec3(x, y, z);
            case ENTITY -> {
                Minecraft minecraft = Minecraft.getInstance();
                Entity target = entity == null || minecraft.level == null ? null : minecraft.level.getEntity(entity);
                if (target == null) yield null;
                // Same points as Flashback's Track Entity keyframe.
                yield switch (bodyPart) {
                    case HEAD -> target.getEyePosition(partialTick);
                    case BODY -> target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
                    case ROOT -> target.getPosition(partialTick);
                };
            }
            case SHAPE -> {
                if (shapeId == null || ShapeTrackRegistry.state(shapeId) == null) yield null;
                Vector3f position = ShapeTrackRegistry.worldTransformOrIdentity(shapeId).getTranslation(new Vector3f());
                yield new Vec3(position.x, position.y, position.z);
            }
        };
    }

    public LookTo withEndsScope(boolean endsScope) {
        return new LookTo(endsScope, kind, entity, bodyPart, x, y, z, shapeId);
    }

    public LookTo withKind(Kind kind) {
        return new LookTo(endsScope, kind, entity, bodyPart, x, y, z, shapeId);
    }

    public LookTo withEntity(@Nullable UUID entity) {
        return new LookTo(endsScope, kind, entity, bodyPart, x, y, z, shapeId);
    }

    public LookTo withBodyPart(TrackingBodyPart bodyPart) {
        return new LookTo(endsScope, kind, entity, bodyPart, x, y, z, shapeId);
    }

    public LookTo withPosition(double x, double y, double z) {
        return new LookTo(endsScope, kind, entity, bodyPart, x, y, z, shapeId);
    }

    public LookTo withShapeId(@Nullable String shapeId) {
        return new LookTo(endsScope, kind, entity, bodyPart, x, y, z, shapeId);
    }
}
