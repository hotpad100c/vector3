package ml.mypals.vectorthree.core.port;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** What the shape gizmo editor is doing, for render code that draws or hides things around it. */
public interface ShapeEditing {
    @Nullable String selectedShapeId();

    /** The gizmo is editing on the screen layer. */
    boolean screenSpace();

    /** The shape is one of the gizmo's own overlay shapes on the screen layer. */
    boolean ownsScreenOverlay(Identifier id);

    ShapeEditing NONE = new ShapeEditing() {
        public @Nullable String selectedShapeId() { return null; }
        public boolean screenSpace() { return false; }
        public boolean ownsScreenOverlay(Identifier id) { return false; }
    };
}
