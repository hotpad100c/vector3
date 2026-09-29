package ml.mypals.vectorthree.mc.render;

import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shapeManagers.EmptyShapeManager;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManager;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Switches RRK shapes off for one render pass and back on afterwards. */
public final class ShapeHider {
    private final List<Shape> hidden = new ArrayList<>();

    public void hide(Predicate<Identifier> which) {
        for (ShapeManager manager : ShapeManagers.managers) {
            for (ShapeManager.ShapeGroup group : List.of(manager.immediateShapeGroup, manager.batchShapeGroup, manager.bufferedShapeGroup)) {
                hide(group.normalShapeMap, which);
                hide(group.seeThroughShapeMap, which);
            }
        }
        for (EmptyShapeManager manager : ShapeManagers.emptyManagers) hide(manager.shapeGroup.shapeMap, which);
    }

    public void restore() {
        for (Shape shape : hidden) shape.enabled = true;
        hidden.clear();
    }

    private void hide(Map<Identifier, Shape> shapes, Predicate<Identifier> which) {
        for (Map.Entry<Identifier, Shape> entry : shapes.entrySet()) {
            if (!entry.getValue().enabled || !which.test(entry.getKey())) continue;
            entry.getValue().enabled = false;
            hidden.add(entry.getValue());
        }
    }
}
