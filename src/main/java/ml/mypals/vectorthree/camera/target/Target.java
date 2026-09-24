package ml.mypals.vectorthree.camera.target;

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

/** Something the camera can be aimed at: an entity, a fixed position or a shape. */
public record Target(Kind kind, @Nullable UUID entity, TrackingBodyPart bodyPart, double x, double y, double z,
                     @Nullable String shapeId) {

    public enum Kind implements ComboOption {
        ENTITY("vector3.target.kind.entity"),
        POSITION("vector3.target.kind.position"),
        SHAPE("vector3.target.kind.shape");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        @Override
        public String text() {
            return I18n.get(key);
        }
    }

    public static Target at(Vec3 position) {
        return new Target(Kind.POSITION, null, TrackingBodyPart.HEAD, position.x, position.y, position.z, null);
    }

    /** Where the target is, or null when its entity or shape isn't loaded. */
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

    /** Fills fields that older saves lack. */
    public Target sanitized() {
        return new Target(kind == null ? Kind.POSITION : kind, entity, bodyPart == null ? TrackingBodyPart.HEAD : bodyPart,
                x, y, z, shapeId);
    }

    public Target withKind(Kind kind) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withEntity(@Nullable UUID entity) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withBodyPart(TrackingBodyPart bodyPart) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withPosition(double x, double y, double z) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withShapeId(@Nullable String shapeId) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }
}
