package ml.mypals.vectorthree.mc.light;

import ml.mypals.vectorthree.core.light.Light;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Moves lights between their parent shape's space and the world, using the shape's pose as it is right now. */
public final class LightParents {
    private LightParents() {}

    /** A light whose parent rides an entity that isn't loaded is not shown, like the shape's own children. */
    public static boolean hidden(Light light) {
        return light.hasParent() && ShapeTrackRegistry.hiddenByMount(light.parent());
    }

    public static Light toWorld(Light light) {
        if (!light.hasParent()) return light;
        return moved(light, ShapeTrackRegistry.worldTransformOrIdentity(light.parent()), "");
    }

    /** {@code world} expressed relative to {@code parent} (or unchanged when there is none). */
    public static Light toLocal(Light world, String parent) {
        if (parent == null || parent.isEmpty()) return world.withParent("");
        Matrix4f matrix = ShapeTrackRegistry.worldTransformOrIdentity(parent);
        if (Math.abs(matrix.determinant()) < 1.0e-10f) return world.withParent(parent);
        return moved(world, matrix.invert(), parent);
    }

    private static Light moved(Light light, Matrix4f matrix, String parent) {
        Vector3f position = matrix.transformPosition(new Vector3f((float) light.position().x,
                (float) light.position().y, (float) light.position().z));
        Vector3f direction = matrix.transformDirection(new Vector3f((float) light.direction().x,
                (float) light.direction().y, (float) light.direction().z));
        if (direction.lengthSquared() < 1.0e-10f) {
            direction.set((float) light.direction().x, (float) light.direction().y, (float) light.direction().z);
        }
        direction.normalize();
        Vector3f size = matrix.getScale(new Vector3f());
        float scale = (size.x + size.y + size.z) / 3;
        return light.placed(new Vec3(position.x, position.y, position.z), new Vec3(direction.x, direction.y, direction.z),
                scale > 1.0e-6f ? scale : 1, parent).sanitized();
    }
}
