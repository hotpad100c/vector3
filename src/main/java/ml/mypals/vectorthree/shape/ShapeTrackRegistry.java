package ml.mypals.vectorthree.shape;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import ml.mypals.ryansrenderingkit.builderManager.BuilderManager;
import ml.mypals.ryansrenderingkit.builderManager.BuilderManagers;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.render.RenderMethod;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.cylinder.CylinderShape;
import ml.mypals.ryansrenderingkit.shape.cylinder.CylinderWireframeShape;
import ml.mypals.ryansrenderingkit.shape.box.BoxWireframeShape;
import ml.mypals.ryansrenderingkit.shape.box.BoxShape;
import ml.mypals.ryansrenderingkit.shape.box.WireframedBoxShape;
import ml.mypals.ryansrenderingkit.shape.line.LineShape;
import ml.mypals.ryansrenderingkit.shape.line.StripLineShape;
import ml.mypals.ryansrenderingkit.shape.round.SphereShape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import ml.mypals.ryansrenderingkit.shape.round.FaceCircleShape;
import ml.mypals.ryansrenderingkit.shape.round.LineCircleShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public final class ShapeTrackRegistry {
    public record Definition(String id, String name, Function<ShapeState, Shape> factory) {}

    private static final Map<String, Definition> TYPES = new LinkedHashMap<>();
    private static final Map<String, Shape> SHAPES = new LinkedHashMap<>();
    private static final Map<String, String> SHAPE_TYPES = new LinkedHashMap<>();
    private static final Map<String, ShapeState> LAST_STATES = new LinkedHashMap<>();
    private static String previewHighlightId;
    private static boolean fixedSeeThroughPipelines;

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
                .build(Shape.RenderingType.BATCH));
        register("box_wireframe", "Box Wireframe", state -> ShapeGenerator.generateBoxWireframe()
                .size(size(state)).edgeWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("wireframed_box", "Solid + Wireframe Box", state -> ShapeGenerator.generateWireframedBox()
                .size(size(state)).edgeWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("sphere", "Sphere", state -> ShapeGenerator.generateSphere()
                .radius((float) state.sizeX() / 2).segments(state.segments()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("face_circle", "Circle", state -> ShapeGenerator.generateFaceCircle()
                .radius((float) state.sizeX() / 2).segments(state.segments()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("line_circle", "Circle Wireframe", state -> ShapeGenerator.generateLineCircle()
                .radius((float) state.sizeX() / 2).segments(state.segments()).lineWidth(state.lineWidth())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("cylinder", "Cylinder", state -> ShapeGenerator.generateCylinder()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("cylinder_wireframe", "Cylinder Wireframe", state -> ShapeGenerator.generateCylinderWireframe()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .width(state.lineWidth()).color(new Color(state.color(), true)).seeThrough(state.seeThrough())
                .build(Shape.RenderingType.BATCH));
        register("cone", "Cone", state -> ShapeGenerator.generateCone()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("cone_wireframe", "Cone Wireframe", state -> ShapeGenerator.generateConeWireframe()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .width(state.lineWidth()).color(new Color(state.color(), true)).seeThrough(state.seeThrough())
                .build(Shape.RenderingType.BATCH));
        register("line", "Line", state -> ShapeGenerator.generateLine()
                .start(point(state, 0)).end(point(state, 1)).lineWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("line_strip", "Line Strip", state -> ShapeGenerator.generateStripLine()
                .vertexes(points(state)).lineWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("text", "Text", state -> {
            TextSettings settings = state.text() == null ? TextSettings.defaults() : state.text();
            return ShapeGenerator.generateText()
                    .texts(Arrays.asList(settings.value().split("\\R", -1)))
                    .textColors(new Color(state.color(), true))
                    .billBoardMode(TextShape.BillBoardMode.valueOf(settings.billboard()))
                    .shadow(settings.shadow()).outline(settings.outline())
                    .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH);
        });
    }

    public static Iterable<Definition> definitions() { return TYPES.values(); }
    public static Definition definition(String id) { return TYPES.get(id); }
    public static Iterable<String> shapeIds() { return List.copyOf(SHAPES.keySet()); }
    public static String typeOf(String shapeId) { return SHAPE_TYPES.get(shapeId); }
    public static void previewHighlight(String shapeId) { previewHighlightId = shapeId; }

    public static void clear() {
        for (String shapeId : List.copyOf(SHAPES.keySet())) {
            ShapeManagers.removeShapes(Identifier.parse(shapeId));
        }
        SHAPES.clear();
        SHAPE_TYPES.clear();
        LAST_STATES.clear();
        previewHighlightId = null;
    }

    public static void apply(ShapeState state) {
        fixSeeThroughPipelines();
        Shape shape = SHAPES.get(state.shapeId());
        ShapeState previous = LAST_STATES.get(state.shapeId());
        boolean immutableWidthChanged = previous != null
                && (state.shapeType().equals("box_wireframe") || state.shapeType().equals("wireframed_box"))
                && previous.lineWidth() != state.lineWidth();
        if (shape != null && (!state.shapeType().equals(SHAPE_TYPES.get(state.shapeId())) || immutableWidthChanged)) {
            ShapeManagers.removeShapes(Identifier.parse(state.shapeId()));
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
            // RRK 1.3.0's forceSetSegments(float) accidentally calls setRadius(float).
            // Set all three targets directly, sync once, then use the working height force-setter
            // only to rebuild geometry from the now-consistent values.
            cylinder.setRadius((float) state.sizeX() / 2);
            cylinder.setSegments(state.segments());
            cylinder.setHeight((float) state.sizeY());
            cylinder.transformer.syncLastToTarget();
            cylinder.forceSetHeight((float) state.sizeY());
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
        if (shape instanceof TextShape textShape) {
            TextSettings settings = state.text() == null ? TextSettings.defaults() : state.text();
            textShape.contents.clear();
            textShape.contents.addAll(Arrays.asList(settings.value().split("\\R", -1)));
            textShape.colors.clear();
            textShape.colors.add(new Color(state.color(), true));
            textShape.shadow = settings.shadow();
            textShape.outline = settings.outline();
            textShape.setBillboardMode(TextShape.BillBoardMode.valueOf(settings.billboard()));
        }
        shape.seeThrough = state.seeThrough();
        applyParent(shape, state.parentShapeId());
        shape.syncLastToTarget();
        Color color = new Color(state.color(), true);
        /*if (state.shapeId().equals(previewHighlightId))
            color = new Color(255, 255, 0, color.getAlpha());
       */ shape.setBaseColor(color);
        if (shape instanceof CylinderShape cylinder) {
            cylinder.color = color;
        }
        if (shape instanceof TextShape textShape) {
            textShape.colors.clear();
            textShape.colors.add(color);
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

    private static void applyParent(Shape shape, String parentId) {
        Shape parent = parentId == null || parentId.isEmpty() ? null : SHAPES.get(parentId);
        if (parent == shape || createsCycle(shape, parent)) parent = null;
        if (shape.parent == parent) return;
        if (parent == null) shape.setParent(null); else parent.addChild(shape);
    }

    private static boolean createsCycle(Shape shape, Shape parent) {
        for (Shape current = parent; current != null; current = current.parent) {
            if (current == shape) return true;
        }
        return false;
    }

    private static void fixSeeThroughPipelines() {
        if (fixedSeeThroughPipelines || BuilderManagers.LINES_BUILDER_MANAGER == null) return;
        replaceSeeThroughPipeline(BuilderManagers.LINES_BUILDER_MANAGER, "see_through_lines_fixed");
        replaceSeeThroughPipeline(BuilderManagers.LINE_STRIP_BUILDER_MANAGER, "see_through_line_strip_fixed");
        fixedSeeThroughPipelines = true;
    }

    private static void replaceSeeThroughPipeline(BuilderManager manager, String name) {
        RenderMethod old = manager.renderMethod;
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("vector3", name))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(Optional.empty())
                .withCull(old.cullFace())
                .withVertexBinding(0, old.format())
                .withPrimitiveTopology(old.mode())
                .build());
        RenderType renderType = RenderType.create(name,
                RenderSetup.builder(pipeline).createRenderSetup());
        manager.renderMethod = new RenderMethod(renderType, old.normalRenderType(),
                old.mode(), old.format(), old.cullFace());
    }
}
