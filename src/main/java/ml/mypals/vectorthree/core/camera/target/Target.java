package ml.mypals.vectorthree.core.camera.target;

import ml.mypals.vectorthree.core.entity.BodyPart;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Something the camera can be aimed at: an entity, a fixed position or a shape. */
public record Target(Kind kind, @Nullable UUID entity, BodyPart bodyPart, double x, double y, double z,
                     @Nullable String shapeId) {

    public enum Kind {
        ENTITY("vector3.target.kind.entity"),
        POSITION("vector3.target.kind.position"),
        SHAPE("vector3.target.kind.shape");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String label() {
            return I18n.get(key);
        }
    }

    public static Target at(Vec3 position) {
        return new Target(Kind.POSITION, null, BodyPart.HEAD, position.x, position.y, position.z, null);
    }

    /** Fills fields that older saves lack. */
    public Target sanitized() {
        return new Target(kind == null ? Kind.POSITION : kind, entity, bodyPart == null ? BodyPart.HEAD : bodyPart,
                x, y, z, shapeId);
    }

    public Target withKind(Kind kind) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withEntity(@Nullable UUID entity) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withBodyPart(BodyPart bodyPart) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withPosition(double x, double y, double z) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }

    public Target withShapeId(@Nullable String shapeId) {
        return new Target(kind, entity, bodyPart, x, y, z, shapeId);
    }
}
