package ml.mypals.vectorthree.core.port;

import ml.mypals.vectorthree.core.shape.ShapeState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** What the shape gizmo editor is doing, for render code that draws or hides things around it. */
public interface ShapeEditing {
    @Nullable String selectedShapeId();

    /** The gizmo is editing on the screen layer. */
    boolean screenSpace();

    /** The shape is one of the gizmo's own overlay shapes on the screen layer. */
    boolean ownsScreenOverlay(Identifier id);

    /** The shape's interpolated state at a timeline tick, or null when it has no track. */
    @Nullable ShapeState stateAt(String shapeId, double tick);

    /** The tick of the last keyframe on the shape's track, or -1. */
    int lastKeyframeTick(String shapeId);

    ShapeEditing NONE = new ShapeEditing() {
        public @Nullable ShapeState stateAt(String shapeId, double tick) { return null; }
        public int lastKeyframeTick(String shapeId) { return -1; }
        public @Nullable String selectedShapeId() { return null; }
        public boolean screenSpace() { return false; }
        public boolean ownsScreenOverlay(Identifier id) { return false; }
    };
}
