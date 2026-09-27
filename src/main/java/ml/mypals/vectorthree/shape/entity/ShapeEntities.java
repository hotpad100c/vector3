package ml.mypals.vectorthree.shape.entity;

import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.EntityShape;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity shapes either spawn their own entity or project one already in the world. Either way the shape has its own
 * UUID, derived from its shape id, which Entity Pose keyframes target to pose it.
 */
public final class ShapeEntities {
    public static final String SOURCE = "source";
    public static final String SOURCE_WORLD = "world";
    public static final String ENTITY = "entity";

    public record Entry(String shapeId, UUID uuid) {}

    private ShapeEntities() {}

    public static UUID uuidOf(String shapeId) {
        return UUID.nameUUIDFromBytes(("vector3:shape_entity/" + shapeId).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean projectsWorldEntity(ShapeState state) {
        return state.blockProperties() != null && SOURCE_WORLD.equals(state.blockProperties().get(SOURCE));
    }

    public static @Nullable UUID source(ShapeState state) {
        String text = state.blockProperties() == null ? null : state.blockProperties().get(ENTITY);
        if (text == null) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Map<String, String> worldSource(@Nullable UUID entity) {
        return entity == null ? Map.of(SOURCE, SOURCE_WORLD) : Map.of(SOURCE, SOURCE_WORLD, ENTITY, entity.toString());
    }

    public static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (String shapeId : ShapeTrackRegistry.shapeIds()) {
            if ("entity".equals(ShapeTrackRegistry.typeOf(shapeId))) entries.add(new Entry(shapeId, uuidOf(shapeId)));
        }
        return entries;
    }

    public static @Nullable String shapeOf(@Nullable UUID uuid) {
        if (uuid == null) return null;
        for (Entry entry : entries()) if (entry.uuid().equals(uuid)) return entry.shapeId();
        return null;
    }

    /** A loaded world entity, or the entity an entity shape draws (its own, or the one it projects). */
    public static @Nullable Entity resolve(@Nullable UUID uuid) {
        if (uuid == null) return null;
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(uuid);
        if (entity != null) return entity;
        String shapeId = shapeOf(uuid);
        if (shapeId == null) return null;
        if (ShapeTrackRegistry.shape(shapeId) instanceof ProjectedEntityShape projected) return projected.source();
        return ShapeTrackRegistry.shape(shapeId) instanceof EntityShape shape ? shape.entity : null;
    }
}
