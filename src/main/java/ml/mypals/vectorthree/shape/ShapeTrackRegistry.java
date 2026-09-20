package ml.mypals.vectorthree.shape;

import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.BoxLikeShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class ShapeTrackRegistry {
    private static final Map<String, Function<ShapeState, Shape>> TYPES = new HashMap<>();
    private static final Map<String, Shape> SHAPES = new HashMap<>();

    private ShapeTrackRegistry() {}

    public static void register(String type, Function<ShapeState, Shape> factory) {
        if (TYPES.putIfAbsent(type, factory) != null) {
            throw new IllegalArgumentException("Duplicate shape track type: " + type);
        }
    }

    public static void registerDefaults() {
        register("cube", state -> ShapeGenerator.generateBoxFace()
                .pos(new Vec3(state.x(), state.y(), state.z()))
                .size(new Vec3(state.sizeX(), state.sizeY(), state.sizeZ()))
                .color(Color.WHITE)
                .seeThrough(state.seeThrough())
                .build(Shape.RenderingType.IMMEDIATE));
    }

    public static void apply(ShapeState state) {
        Shape shape = SHAPES.get(state.shapeId());
        if (shape == null) {
            Function<ShapeState, Shape> factory = TYPES.get(state.shapeType());
            if (factory == null) return;
            shape = factory.apply(state);
            ShapeManagers.addShape(Identifier.parse(state.shapeId()), shape);
            SHAPES.put(state.shapeId(), shape);
        }

        shape.transformer.setShapeWorldPivot(new Vec3(state.x(), state.y(), state.z()));
        shape.transformer.setShapeWorldRotationDegrees(state.pitch(), state.yaw(), state.roll());
        shape.transformer.setShapeWorldScale(new Vec3(state.scaleX(), state.scaleY(), state.scaleZ()));
        if (shape instanceof BoxLikeShape box) {
            box.setDimension(new Vec3(state.sizeX(), state.sizeY(), state.sizeZ()));
        }
        shape.seeThrough = state.seeThrough();
        if (state.visible()) shape.enable(); else shape.disable();
    }
}
