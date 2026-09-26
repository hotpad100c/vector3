package ml.mypals.vectorthree.multiedit;

import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Moves, turns and scales several shape keyframes as one, about a shared pivot or each about its own center. */
public final class GroupTransform {
    public record Member(ShapeKeyframe keyframe, int track, int tick) {}

    private GroupTransform() {}

    public static Vec3 pivot(Collection<ShapeState> states) {
        Vec3 sum = Vec3.ZERO;
        for (ShapeState state : states) sum = sum.add(ShapeGizmoEditor.center(state));
        return states.isEmpty() ? sum : sum.scale(1.0 / states.size());
    }

    /** A shape whose parent chain holds another selected shape already follows that one. */
    public static boolean followsSelected(ShapeState state, Set<String> selected) {
        Set<String> seen = new HashSet<>();
        String parent = state.parentShapeId();
        while (parent != null && !parent.isEmpty() && seen.add(parent)) {
            if (selected.contains(parent)) return true;
            ShapeState parentState = ShapeTrackRegistry.state(parent);
            parent = parentState == null ? null : parentState.parentShapeId();
        }
        return false;
    }

    public static ShapeState move(ShapeState start, Vec3 delta) {
        return ShapeGizmoEditor.withPosition(start, ShapeGizmoEditor.center(start).add(delta));
    }

    /** Global turns about {@code worldAxis} through the pivot; local turns each shape in place about its own axis. */
    public static ShapeState rotate(ShapeState start, Vec3 pivot, Vec3 worldAxis, int axis, double degrees, boolean local) {
        if (local) {
            return ShapeGizmoEditor.withRotation(start,
                    ShapeGizmoEditor.rotatedAbout(start, ShapeGizmoEditor.localAxis(start, axis), degrees));
        }
        Vector3f offset = ShapeGizmoEditor.center(start).subtract(pivot).toVector3f()
                .rotate(new Quaternionf().rotateAxis((float) Math.toRadians(degrees), worldAxis.toVector3f().normalize()));
        ShapeState moved = ShapeGizmoEditor.withPosition(start, pivot.add(offset.x, offset.y, offset.z));
        return ShapeGizmoEditor.withRotation(moved, ShapeGizmoEditor.rotatedAbout(start, worldAxis, degrees));
    }

    /**
     * Global scaling pushes centers away from the pivot (along {@code worldAxis} only, when given) and spreads the
     * stretch over each shape's own axes; local scaling only grows each shape's own axis, or all three.
     */
    public static ShapeState scale(ShapeState start, Vec3 pivot, @Nullable Vec3 worldAxis, int axis, double factor, boolean local) {
        float[] scale = {(float) start.scaleX(), (float) start.scaleY(), (float) start.scaleZ()};
        if (local) {
            for (int i = 0; i < 3; i++) {
                if (worldAxis == null || i == axis) scale[i] = (float) Math.max(0.001, scale[i] * factor);
            }
            return ShapeGizmoEditor.withScale(start, scale);
        }
        Vec3 offset = ShapeGizmoEditor.center(start).subtract(pivot);
        Vec3 scaledOffset;
        if (worldAxis == null) {
            scaledOffset = offset.scale(factor);
            for (int i = 0; i < 3; i++) scale[i] = (float) Math.max(0.001, scale[i] * factor);
        } else {
            Vec3 direction = worldAxis.normalize();
            scaledOffset = offset.add(direction.scale(offset.dot(direction) * (factor - 1)));
            for (int i = 0; i < 3; i++) {
                double alignment = ShapeGizmoEditor.localAxis(start, i).dot(direction);
                scale[i] = (float) Math.max(0.001, scale[i] * (1 + (factor - 1) * alignment * alignment));
            }
        }
        ShapeState moved = ShapeGizmoEditor.withPosition(start, pivot.add(scaledOffset));
        return ShapeGizmoEditor.withScale(moved, scale);
    }
}
