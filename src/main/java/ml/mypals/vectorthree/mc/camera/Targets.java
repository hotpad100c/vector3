package ml.mypals.vectorthree.mc.camera;

import ml.mypals.vectorthree.core.camera.target.Target;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class Targets {
    private Targets() {}

    /** Where the target is, or null when its entity or shape isn't loaded. */
    public static @Nullable Vec3 resolve(Target target, float partialTick) {
        return switch (target.kind()) {
            case POSITION -> new Vec3(target.x(), target.y(), target.z());
            case ENTITY -> {
                Minecraft minecraft = Minecraft.getInstance();
                Entity entity = target.entity() == null || minecraft.level == null ? null : minecraft.level.getEntity(target.entity());
                if (entity == null) yield null;
                // Same points as Flashback's Track Entity keyframe.
                yield switch (target.bodyPart()) {
                    case HEAD -> entity.getEyePosition(partialTick);
                    case BODY -> entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.5, 0);
                    case ROOT -> entity.getPosition(partialTick);
                };
            }
            case SHAPE -> {
                String shapeId = target.shapeId();
                if (shapeId == null || ShapeTrackRegistry.state(shapeId) == null) yield null;
                Vector3f position = ShapeTrackRegistry.worldTransformOrIdentity(shapeId).getTranslation(new Vector3f());
                yield new Vec3(position.x, position.y, position.z);
            }
        };
    }
}
