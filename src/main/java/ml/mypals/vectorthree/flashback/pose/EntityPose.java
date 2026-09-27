package ml.mypals.vectorthree.flashback.pose;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One track's pose for one entity: per model part (by the model's part names, plus {@link #ROOT}), a rotation in
 * degrees and an offset in model units (1/16 block), each switched on separately. Several tracks may pose the same
 * entity; they apply in track order, each on top of the previous, so one track can drive a part's rotation and
 * another its offset. Channels left off keep what the animation (or an earlier track) gave them.
 */
public record EntityPose(@Nullable UUID entity, Mode mode, Map<String, Limb> parts) {
    public static final String ROOT = "root";

    public enum Mode {
        /** The rotation replaces what the part had; the offset is added to the part's rest position. */
        ABSOLUTE,
        /** Both are added on top of what the part had. */
        ADDITIVE
    }

    public record Limb(boolean rotate, boolean move, float xRot, float yRot, float zRot, float x, float y, float z) {
        public static final Limb NONE = new Limb(false, false, 0, 0, 0, 0, 0, 0);

        public boolean active() {
            return rotate || move;
        }

        public Limb withRotate(boolean rotate) {
            return new Limb(rotate, move, xRot, yRot, zRot, x, y, z);
        }

        public Limb withMove(boolean move) {
            return new Limb(rotate, move, xRot, yRot, zRot, x, y, z);
        }

        public Limb withRotation(float xRot, float yRot, float zRot) {
            return new Limb(rotate, move, xRot, yRot, zRot, x, y, z);
        }

        public Limb withOffset(float x, float y, float z) {
            return new Limb(rotate, move, xRot, yRot, zRot, x, y, z);
        }

        // Each channel blends when both sides use it, and otherwise holds the side that does.
        Limb lerp(Limb to, float amount) {
            Limb rotation = rotate && to.rotate ? new Limb(true, false, Mth.rotLerp(amount, xRot, to.xRot),
                    Mth.rotLerp(amount, yRot, to.yRot), Mth.rotLerp(amount, zRot, to.zRot), 0, 0, 0)
                    : rotate ? this : to.rotate ? to : NONE;
            Limb offset = move && to.move ? new Limb(false, true, 0, 0, 0, Mth.lerp(amount, x, to.x),
                    Mth.lerp(amount, y, to.y), Mth.lerp(amount, z, to.z))
                    : move ? this : to.move ? to : NONE;
            return new Limb(rotation.rotate, offset.move, rotation.xRot, rotation.yRot, rotation.zRot,
                    offset.x, offset.y, offset.z);
        }
    }

    public EntityPose {
        parts = parts == null ? Map.of() : Map.copyOf(parts);
        if (mode == null) mode = Mode.ABSOLUTE;
    }

    public EntityPose withEntity(@Nullable UUID entity) {
        return new EntityPose(entity, mode, parts);
    }

    public EntityPose withMode(Mode mode) {
        return new EntityPose(entity, mode, parts);
    }

    public EntityPose withPart(String name, Limb limb) {
        Map<String, Limb> next = new LinkedHashMap<>(parts);
        next.put(name, limb);
        return new EntityPose(entity, mode, next);
    }

    public EntityPose withParts(Map<String, Limb> parts) {
        return new EntityPose(entity, mode, parts);
    }

    EntityPose lerp(EntityPose to, float amount) {
        if (!Objects.equals(entity, to.entity) || mode != to.mode) return amount < 0.5f ? this : to;
        Map<String, Limb> blended = new LinkedHashMap<>();
        for (Map.Entry<String, Limb> entry : parts.entrySet()) {
            Limb other = to.parts.getOrDefault(entry.getKey(), Limb.NONE);
            Limb limb = entry.getValue().lerp(other, amount);
            if (limb.active()) blended.put(entry.getKey(), limb);
        }
        for (Map.Entry<String, Limb> entry : to.parts.entrySet()) {
            if (!blended.containsKey(entry.getKey()) && entry.getValue().active()) blended.put(entry.getKey(), entry.getValue());
        }
        return new EntityPose(entity, mode, blended);
    }
}
