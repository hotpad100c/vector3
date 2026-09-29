package ml.mypals.vectorthree.core.shape;

import ml.mypals.vectorthree.core.entity.BodyPart;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


/** {@code modelPart}, when set, mounts on that part of the entity's model instead of on {@code part}. */
public record ShapeMount(UUID entity, BodyPart part, boolean followRotation, @Nullable String modelPart) {
    public ShapeMount(UUID entity, BodyPart part, boolean followRotation) {
        this(entity, part, followRotation, null);
    }

    public ShapeMount sanitized() {
        return part == null ? new ShapeMount(entity, BodyPart.ROOT, followRotation, modelPart) : this;
    }

    public ShapeMount withPart(BodyPart part) {
        return new ShapeMount(entity, part, followRotation, modelPart);
    }

    public ShapeMount withFollowRotation(boolean followRotation) {
        return new ShapeMount(entity, part, followRotation, modelPart);
    }

    public ShapeMount withModelPart(@Nullable String modelPart) {
        return new ShapeMount(entity, part, followRotation, modelPart);
    }
}
