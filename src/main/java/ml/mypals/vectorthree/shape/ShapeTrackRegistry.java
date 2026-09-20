package ml.mypals.vectorthree.shape;

import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.cylinder.CylinderShape;
import ml.mypals.ryansrenderingkit.shape.cylinder.CylinderWireframeShape;
import ml.mypals.ryansrenderingkit.shape.box.BoxWireframeShape;
import ml.mypals.ryansrenderingkit.shape.box.BoxShape;
import ml.mypals.ryansrenderingkit.shape.box.WireframedBoxShape;
import ml.mypals.ryansrenderingkit.shape.line.LineShape;
import ml.mypals.ryansrenderingkit.shape.line.StripLineShape;
import ml.mypals.ryansrenderingkit.shape.round.SphereShape;
import ml.mypals.ryansrenderingkit.shape.round.FaceCircleShape;
import ml.mypals.ryansrenderingkit.shape.round.LineCircleShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Function;

public final class ShapeTrackRegistry {
    public record Definition(String id, String name, Function<ShapeState, Shape> factory) {}

    private static final Map<String, Definition> TYPES = new LinkedHashMap<>();
    private static final Map<String, Shape> SHAPES = new LinkedHashMap<>();
    private static final Map<String, String> SHAPE_TYPES = new LinkedHashMap<>();
    private static final Map<String, ShapeState> LAST_STATES = new LinkedHashMap<>();
    private static String previewHighlightId;

    private ShapeTrackRegistry() {}

    public static void register(String type, String name, Function<ShapeState, Shape> factory) {
        Definition definition = new Definition(type, name, factory);
        if (TYPES.putIfAbsent(type, definition) != null) {
            throw new IllegalArgumentException("Duplicate shape track type: " + type);
        }
    }

    public static void registerDefaults() {
        register("box", "Solid Box", state -> ShapeGenerator.generateBoxFace()
                .pos(new Vec3(state.x(), state.y(), state.z()))
                .size(new Vec3(state.sizeX(), state.sizeY(), state.sizeZ()))
                .color(new Color(state.color(), true))
                .seeThrough(state.seeThrough())
                .build(Shape.RenderingType.IMMEDIATE));
        register("box_wireframe", "Box Wireframe", state -> ShapeGenerator.generateBoxWireframe()
                .size(size(state)).edgeWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("wireframed_box", "Solid + Wireframe Box", state -> ShapeGenerator.generateWireframedBox()
                .size(size(state)).edgeWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("sphere", "Sphere", state -> ShapeGenerator.generateSphere()
                .radius((float) state.sizeX() / 2).segments(state.segments()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("face_circle", "Circle", state -> ShapeGenerator.generateFaceCircle()
                .radius((float) state.sizeX() / 2).segments(state.segments()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("line_circle", "Circle Wireframe", state -> ShapeGenerator.generateLineCircle()
                .radius((float) state.sizeX() / 2).segments(state.segments()).lineWidth(state.lineWidth())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("cylinder", "Cylinder", state -> ShapeGenerator.generateCylinder()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("cylinder_wireframe", "Cylinder Wireframe", state -> ShapeGenerator.generateCylinderWireframe()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .width(state.lineWidth()).color(new Color(state.color(), true)).seeThrough(state.seeThrough())
                .build(Shape.RenderingType.IMMEDIATE));
        register("cone", "Cone", state -> ShapeGenerator.generateCone()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("cone_wireframe", "Cone Wireframe", state -> ShapeGenerator.generateConeWireframe()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .width(state.lineWidth()).color(new Color(state.color(), true)).seeThrough(state.seeThrough())
                .build(Shape.RenderingType.IMMEDIATE));
        register("line", "Line", state -> ShapeGenerator.generateLine()
                .start(point(state, 0)).end(point(state, 1)).lineWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
        register("line_strip", "Line Strip", state -> ShapeGenerator.generateStripLine()
                .vertexes(points(state)).lineWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.IMMEDIATE));
    }

    public static Iterable<Definition> definitions() { return TYPES.values(); }
    public static Definition definition(String id) { return TYPES.get(id); }
    public static Iterable<String> shapeIds() { return List.copyOf(SHAPES.keySet()); }
    public static String typeOf(String shapeId) { return SHAPE_TYPES.get(shapeId); }
    public static void previewHighlight(String shapeId) { previewHighlightId = shapeId; }

    public static void apply(ShapeState state) {
        Shape shape = SHAPES.get(state.shapeId());
        ShapeState previous = LAST_STATES.get(state.shapeId());
        boolean immutableWidthChanged = previous != null
                && (state.shapeType().equals("box_wireframe") || state.shapeType().equals("wireframed_box"))
                && previous.lineWidth() != state.lineWidth();
        if (shape != null && (!state.shapeType().equals(SHAPE_TYPES.get(state.shapeId())) || immutableWidthChanged)) {
            ShapeManagers.removeShape(Identifier.parse(state.shapeId()));
            SHAPES.remove(state.shapeId());
            shape = null;
        }
        if (shape == null) {
            Definition definition = TYPES.get(state.shapeType());
            if (definition == null) return;
            shape = definition.factory().apply(state);
            ShapeManagers.addShape(Identifier.parse(state.shapeId()), shape);
            SHAPES.put(state.shapeId(), shape);
            SHAPE_TYPES.put(state.shapeId(), state.shapeType());
        }

        shape.forceSetWorldPosition(new Vec3(state.x(), state.y(), state.z()));
        shape.forceSetWorldRotation(new Vector3f(state.pitch(), state.yaw(), state.roll()));
        shape.forceSetWorldScale(new Vec3(state.scaleX(), state.scaleY(), state.scaleZ()));
        if (shape instanceof BoxShape box) box.forceSetDimensions(size(state));
        if (shape instanceof WireframedBoxShape wireframed) {
            wireframed.edgeWidth = state.lineWidth();
            wireframed.lineSeeThrough = state.seeThrough();
        }
        if (shape instanceof LineCircleShape circle) {
            circle.forceSetRadius((float) state.sizeX() / 2);
            circle.forceSetSegments(state.segments());
            circle.forceSetLineWidth(state.lineWidth());
        } else if (shape instanceof FaceCircleShape circle) {
            circle.forceSetRadius((float) state.sizeX() / 2);
            circle.forceSetSegments(state.segments());
        } else if (shape instanceof SphereShape sphere) {
            sphere.setRadius((float) state.sizeX() / 2);
            sphere.setSegments(state.segments());
            sphere.transformer.syncLastToTarget();
            sphere.generateSphereShape(false);
        }
        if (shape instanceof CylinderShape cylinder) {
            cylinder.forceSetRadius((float) state.sizeX() / 2);
            cylinder.forceSetHeight((float) state.sizeY());
            cylinder.forceSetSegments(state.segments());
        }
        if (shape instanceof CylinderWireframeShape wireframe) {
            wireframe.forceSetRadius((float) state.sizeX() / 2);
            wireframe.forceSetLineWidth(state.lineWidth());
        }
        if (shape instanceof LineShape line) {
            line.forceSetStart(point(state, 0));
            line.forceSetEnd(point(state, 1));
            line.forceSetLineWidth(state.lineWidth());
        }
        if (shape instanceof StripLineShape strip) {
            strip.setVertexes(points(state));
            strip.forceSetLineWidth(state.lineWidth());
        }
        shape.seeThrough = state.seeThrough();
        shape.syncLastToTarget();
        Color color = new Color(state.color(), true);
        /*if (state.shapeId().equals(previewHighlightId))
            color = new Color(255, 255, 0, color.getAlpha());
       */ shape.setBaseColor(color);
        if (shape instanceof CylinderShape cylinder) {
            cylinder.color = color;
        }
        if (shape instanceof WireframedBoxShape wireframed) {
            wireframed.faceputColor = color;
            wireframed.edgeputColor = color;
        }
        if (state.visible()) shape.enable(); else shape.disable();
        LAST_STATES.put(state.shapeId(), state);

    }

    private static Vec3 size(ShapeState state) {
        return new Vec3(state.sizeX(), state.sizeY(), state.sizeZ());
    }

    private static Vec3 point(ShapeState state, int index) {
        return state.points() != null && state.points().size() > index
                ? state.points().get(index).vec3() : Vec3.ZERO;
    }

    private static List<Vec3> points(ShapeState state) {
        return state.points() == null ? List.of() : state.points().stream().map(ShapePoint::vec3).toList();
    }
}
