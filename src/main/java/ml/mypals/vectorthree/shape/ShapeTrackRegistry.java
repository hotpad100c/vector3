package ml.mypals.vectorthree.shape;

import ml.mypals.vectorthree.compat.IrisCompat;
import com.mojang.renderpearl.api.pipeline.*;
import ml.mypals.ryansrenderingkit.builderManager.BuilderManager;
import ml.mypals.ryansrenderingkit.builderManager.BuilderManagers;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.render.RenderMethod;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.cylinder.CylinderShape;
import ml.mypals.ryansrenderingkit.shape.cylinder.CylinderWireframeShape;
import ml.mypals.ryansrenderingkit.shape.box.BoxWireframeShape;
import ml.mypals.ryansrenderingkit.shape.box.BoxShape;
import ml.mypals.ryansrenderingkit.shape.line.LineShape;
import ml.mypals.ryansrenderingkit.shape.line.StripLineShape;
import ml.mypals.ryansrenderingkit.shape.round.SphereShape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shape.round.FaceCircleShape;
import ml.mypals.ryansrenderingkit.shape.round.LineCircleShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.ryansrenderingkit.shapeManagers.VertexBuilderGetter;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.vectorthree.shape.area.AreaShape;
import ml.mypals.vectorthree.shape.arrow.ArrowShape;
import ml.mypals.vectorthree.shape.media.ImageShape;
import ml.mypals.vectorthree.shape.media.VideoShape;
import ml.mypals.vectorthree.shape.point.ShapePoint;
import ml.mypals.vectorthree.shape.text.FontTextShape;
import ml.mypals.vectorthree.shape.text.TextSettings;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.function.Function;

public final class ShapeTrackRegistry {
    public record Definition(String id, String name, Function<ShapeState, Shape> factory) {}

    private static final Map<String, Definition> TYPES = new LinkedHashMap<>();
    private static final Map<String, Shape> SHAPES = new LinkedHashMap<>();
    private static final Map<String, String> SHAPE_TYPES = new LinkedHashMap<>();
    private static final Map<String, ShapeState> LAST_STATES = new LinkedHashMap<>();
    private static final Map<String, List<WireframePart>> WIREFRAMES = new LinkedHashMap<>();
    private static final Map<String, String> WIREFRAME_TYPES = new LinkedHashMap<>();
    private static List<String> blockIds;
    private static List<String> itemIds;
    private static List<String> entityIds;
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
        VertexBuilderGetter.registerEmptyShapeBuilder(FontTextShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        VertexBuilderGetter.registerEmptyShapeBuilder(ImageShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        VertexBuilderGetter.registerEmptyShapeBuilder(VideoShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        VertexBuilderGetter.registerEmptyShapeBuilder(AreaShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        VertexBuilderGetter.registerShapeBuilder(ArrowShape.class, ShapeManagers.TRIANGLES_SHAPE_MANAGER);
        register("box", "vector3.shape.box", state -> ShapeGenerator.generateBoxFace()
                .pos(new Vec3(state.x(), state.y(), state.z()))
                .size(new Vec3(state.sizeX(), state.sizeY(), state.sizeZ()))
                .color(new Color(state.color(), true))
                .seeThrough(state.seeThrough())
                .build(Shape.RenderingType.BATCH));
        register("sphere", "vector3.shape.sphere", state -> ShapeGenerator.generateSphere()
                .radius((float) state.sizeX() / 2).segments(state.segments()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("face_circle", "vector3.shape.face_circle", state -> ShapeGenerator.generateFaceCircle()
                .radius((float) state.sizeX() / 2).segments(state.segments()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("cylinder", "vector3.shape.cylinder", state -> ShapeGenerator.generateCylinder()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("cone", "vector3.shape.cone", state -> ShapeGenerator.generateCone()
                .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                .color(new Color(state.color(), true)).seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("line", "vector3.shape.line", state -> ShapeGenerator.generateLine()
                .start(point(state, 0)).end(point(state, 1)).lineWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("line_strip", "vector3.shape.line_strip", state -> ShapeGenerator.generateStripLine()
                .vertexes(points(state)).lineWidth(state.lineWidth()).color(new Color(state.color(), true))
                .seeThrough(state.seeThrough()).build(Shape.RenderingType.BATCH));
        register("text", "vector3.shape.text", state -> {
            TextSettings settings = state.text() == null ? TextSettings.defaults() : state.text();
            return new FontTextShape(settings, new Color(state.color(), true), state.seeThrough());
        });
        register("block", "vector3.shape.block", state -> ShapeGenerator.generateBlock()
                .block(blockState(state.model(), state.blockProperties())).build());
        register("item", "vector3.shape.item", state -> ShapeGenerator.generateItem()
                .itemStack(new ItemStack(item(state))).build());
        register("entity", "vector3.shape.entity", state -> ShapeGenerator.generateEntity()
                .entity(entity(state)).light(0xF000F0).build(Shape.RenderingType.BATCH));
        register("obj", "vector3.shape.obj", state -> new ObjModelShape(Shape.RenderingType.BATCH,
                transformer -> {}, objModelId(state.model()), Vec3.ZERO,
                new Color(state.color(), true), state.seeThrough()));
        register("arrow", "vector3.shape.arrow", state -> new ArrowShape(point(state, 0), point(state, 1),
                state.lineWidth(), (float) state.sizeX(), new Color(state.color(), true), state.seeThrough()));
        register("image", "vector3.shape.image", state -> new ImageShape(state.model(),
                new Color(state.color(), true), state.seeThrough()));
        register("video", "vector3.shape.video", state -> new VideoShape(state.model(),
                new Color(state.color(), true), state.seeThrough()));
        register("area", "vector3.shape.area", state -> new AreaShape(state,
                new Color(state.color(), true), state.seeThrough()));
    }

    public static Iterable<Definition> definitions() { return TYPES.values(); }
    public static Definition definition(String id) { return TYPES.get(id); }
    public static Shape shape(String shapeId) { return SHAPES.get(shapeId); }
    public static Iterable<String> shapeIds() { return List.copyOf(SHAPES.keySet()); }
    public static String typeOf(String shapeId) { return SHAPE_TYPES.get(shapeId); }
    public static void previewHighlight(String shapeId) { previewHighlightId = shapeId; }
    public static ShapeState state(String shapeId) { return LAST_STATES.get(shapeId); }

    public static String displayName(String shapeId) {
        if (shapeId == null) return "";
        ShapeState state = LAST_STATES.get(shapeId);
        String name = state != null ? state.name() : null;
        if (name != null && !name.isBlank()) return name;
        String type = state != null ? state.shapeType() : SHAPE_TYPES.get(shapeId);
        Definition definition = type != null ? TYPES.get(type) : null;
        String label = definition != null ? I18n.get(definition.name())
                : type != null ? type : I18n.get("vector3.shape.unknown");
        return label + " (" + shortId(shapeId) + ")";
    }

    private static String shortId(String shapeId) {
        int slash = shapeId.lastIndexOf('/');
        String id = slash >= 0 ? shapeId.substring(slash + 1) : shapeId;
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    public static String pickShape(RayModelIntersection.Ray ray) {
        String closestId = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, Shape> entry : SHAPES.entrySet()) {
            Shape shape = entry.getValue();
            ShapeState state = LAST_STATES.get(entry.getKey());
            if (state == null || !state.visible()) continue;
            double distance = hitDistance(ray, shape, state);
            if (distance >= 0 && distance < closestDistance) {
                closestId = entry.getKey();
                closestDistance = distance;
            }
        }
        return closestId;
    }

    private static double hitDistance(RayModelIntersection.Ray ray, Shape shape, ShapeState state) {
        if (shape instanceof FontTextShape text) return text.hitDistance(ray);
        List<Vec3> model = shape.getModel(false);
        int[] indices = shape.indexBuffer;
        if (indices == null || indices.length == 0) {
            return rayToPoint(ray, new Vec3(state.x(), state.y(), state.z()));
        }
        boolean lines = switch (state.shapeType()) {
            case "line", "line_strip" -> true;
            default -> false;
        };
        if (lines || indices.length % 3 != 0) return rayToSegments(ray, model, indices);
        RayModelIntersection.HitResult hit = RayModelIntersection.rayIntersectsModel(ray, model, indices);
        return hit.hit ? hit.distance : -1;
    }

    private static double rayToSegments(RayModelIntersection.Ray ray, List<Vec3> vertices, int[] indices) {
        double closest = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < indices.length; i += 2) {
            int first = indices[i];
            int second = indices[i + 1];
            if (first < 0 || second < 0 || first >= vertices.size() || second >= vertices.size()) continue;
            double distance = rayToSegment(ray, vertices.get(first), vertices.get(second));
            if (distance >= 0 && distance < closest) closest = distance;
        }
        return closest == Double.POSITIVE_INFINITY ? -1 : closest;
    }

    private static double rayToSegment(RayModelIntersection.Ray ray, Vec3 start, Vec3 end) {
        Vec3 rayDirection = ray.direction.normalize();
        Vec3 segment = end.subtract(start);
        double segmentLengthSquared = segment.lengthSqr();
        if (segmentLengthSquared < 1.0e-10) return rayToPoint(ray, start);

        Vec3 offset = ray.origin.subtract(start);
        double directionSegment = rayDirection.dot(segment);
        double directionOffset = rayDirection.dot(offset);
        double segmentOffset = segment.dot(offset);
        double denominator = segmentLengthSquared - directionSegment * directionSegment;
        double segmentAmount = Math.abs(denominator) < 1.0e-10 ? 0
                : (segmentOffset - directionSegment * directionOffset) / denominator;
        segmentAmount = Math.clamp(segmentAmount, 0, 1);
        double rayAmount = Math.max(0, directionSegment * segmentAmount - directionOffset);
        Vec3 rayPoint = ray.origin.add(rayDirection.scale(rayAmount));
        Vec3 segmentPoint = start.add(segment.scale(segmentAmount));
        double tolerance = Math.max(0.04, rayAmount * 0.012);
        return rayPoint.distanceToSqr(segmentPoint) <= tolerance * tolerance ? rayAmount : -1;
    }

    private static double rayToPoint(RayModelIntersection.Ray ray, Vec3 point) {
        Vec3 direction = ray.direction.normalize();
        double distance = Math.max(0, point.subtract(ray.origin).dot(direction));
        double tolerance = Math.max(0.1, distance * 0.04);
        return ray.origin.add(direction.scale(distance)).distanceToSqr(point) <= tolerance * tolerance
                ? distance : -1;
    }

    public static void clear() {
        for (String shapeId : List.copyOf(SHAPES.keySet())) {
            removeWireframe(shapeId);
            ShapeManagers.removeShapes(Identifier.parse(shapeId));
            SHAPES.get(shapeId).discard();
        }
        SHAPES.clear();
        SHAPE_TYPES.clear();
        LAST_STATES.clear();
        previewHighlightId = null;
        ImageShape.clearTextures();
    }

    public static void retainOnly(java.util.Set<String> liveShapeIds) {
        for (String shapeId : List.copyOf(SHAPES.keySet())) {
            if (liveShapeIds.contains(shapeId)) continue;
            removeWireframe(shapeId);
            ShapeManagers.removeShapes(Identifier.parse(shapeId));
            SHAPES.get(shapeId).discard();
            SHAPES.remove(shapeId);
            SHAPE_TYPES.remove(shapeId);
            LAST_STATES.remove(shapeId);
        }
        refreshAreaParents();
    }

    public static void apply(ShapeState state) {
        fixSeeThroughPipelines();
        Shape shape = SHAPES.get(state.shapeId());
        ShapeState previous = LAST_STATES.get(state.shapeId());
        boolean modelChanged = previous != null
                && (state.shapeType().equals("obj") || state.shapeType().equals("block")
                        || state.shapeType().equals("item") || state.shapeType().equals("entity")
                        || state.shapeType().equals("image")
                        || state.shapeType().equals("video"))
                && (!java.util.Objects.equals(previous.model(), state.model())
                        || !java.util.Objects.equals(previous.blockProperties(), state.blockProperties()));
        boolean areaBoundsChanged = previous != null && state.shapeType().equals("area")
                && !java.util.Objects.equals(previous.points(), state.points());
        if (shape != null && (!state.shapeType().equals(SHAPE_TYPES.get(state.shapeId()))
                || modelChanged || areaBoundsChanged)) {
            removeWireframe(state.shapeId());
            ShapeManagers.removeShapes(Identifier.parse(state.shapeId()));
            shape.discard();
            SHAPES.remove(state.shapeId());
            shape = null;
        }
        if (shape == null) {
            Definition definition = TYPES.get(state.shapeType());
            if (definition == null) return;
            shape = definition.factory().apply(state);
            ensureCustomManager(shape);
            ShapeManagers.addShape(Identifier.parse(state.shapeId()), shape);
            SHAPES.put(state.shapeId(), shape);
            SHAPE_TYPES.put(state.shapeId(), state.shapeType());
        }

        shape.forceSetWorldPosition(new Vec3(state.x(), state.y(), state.z()));
        shape.forceSetWorldRotation(new Vector3f(state.pitch(), state.yaw(), state.roll()));
        shape.forceSetWorldScale(new Vec3(state.scaleX(), state.scaleY(), state.scaleZ()));
        if (shape instanceof AreaShape area) area.updateTransform(state);
        updateGeometry(shape, state);
        if (shape instanceof VideoShape video) video.setPlayback(state.videoStartTick(),
                !state.manualPlayback(), !state.noLoop(), (float) state.playbackSeconds());
        if (shape instanceof TextShape textShape) {
            TextSettings settings = state.text() == null ? TextSettings.defaults() : state.text();
            textShape.contents.clear();
            textShape.contents.addAll(Arrays.asList(settings.value().split("\\R", -1)));
            textShape.colors.clear();
            textShape.colors.add(new Color(state.color(), true));
            textShape.shadow = settings.shadow();
            textShape.outline = settings.outline();
            textShape.setBillboardMode(TextShape.BillBoardMode.valueOf(settings.billboard()));
            if (textShape instanceof FontTextShape fontShape) {
                fontShape.font = settings.fontOrDefault();
                fontShape.outlineColor = state.outlineColor();
                fontShape.glow = settings.glow();
                fontShape.outlineGlow = settings.outlineGlow();
                fontShape.glowStrength = settings.glowStrengthOrDefault();
                fontShape.glowColor = settings.glowColor();
                fontShape.glowSpread = settings.glowSpreadOrDefault();
            }
        }
        if (previous != null && previous.seeThrough() != state.seeThrough()) {
            ShapeManagers.removeShapes(Identifier.parse(state.shapeId()));
            shape.seeThrough = state.seeThrough();
            ShapeManagers.addShape(Identifier.parse(state.shapeId()), shape);
        } else {
            shape.seeThrough = state.seeThrough();
        }
        applyParent(shape, state.parentShapeId());
        shape.syncLastToTarget();
        Color color = new Color(state.color(), true);
        shape.setBaseColor(color);
        if (shape instanceof CylinderShape cylinder) {
            cylinder.color = color;
        }
        if (shape instanceof TextShape textShape) {
            textShape.colors.clear();
            textShape.colors.add(color);
        }
        if (usesBypassOption(state.shapeType())) shape.customData.put(IrisBypassTarget.SHAPE_FLAG, state.bypassShaders());
        if (state.visible() && !WireframeSettings.orDefault(state.wireframe()).hideFaces()) shape.enable();
        else shape.disable();
        syncWireframe(shape, state, previous);
        LAST_STATES.put(state.shapeId(), state);
        refreshAreaParents();
    }

    // Areas draw themselves instead of through RRK's transformer, so parent motion is pushed to them here.
    private static void refreshAreaParents() {
        for (Map.Entry<String, Shape> entry : SHAPES.entrySet()) {
            if (!(entry.getValue() instanceof AreaShape area)) continue;
            ShapeState state = LAST_STATES.get(entry.getKey());
            area.setParentTransform(worldTransformOrIdentity(state == null ? null : state.parentShapeId()));
        }
    }

    private static void updateGeometry(Shape shape, ShapeState state) {
        // BoxWireframeShape's width is fixed at construction (its setLineWidth casts to the wrong
        // transformer), so syncWireframe rebuilds it when the width changes instead.
        if (shape instanceof BoxShape box) box.forceSetDimensions(size(state));
        // RRK's circle forceSetSegments(float) sets the radius instead (same bug as the cylinder below),
        // so set the targets directly, sync once, and rebuild through the working radius force-setter.
        if (shape instanceof LineCircleShape circle) {
            circle.setSegments(state.segments());
            circle.setLineWidth(state.lineWidth());
            circle.setRadius((float) state.sizeX() / 2);
            circle.transformer.syncLastToTarget();
            circle.forceSetRadius((float) state.sizeX() / 2);
        } else if (shape instanceof FaceCircleShape circle) {
            circle.setSegments(state.segments());
            circle.setRadius((float) state.sizeX() / 2);
            circle.transformer.syncLastToTarget();
            circle.forceSetRadius((float) state.sizeX() / 2);
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
        if (shape instanceof ArrowShape arrow)
            arrow.forceSet(point(state, 0), point(state, 1), state.lineWidth(), (float) state.sizeX());
    }

    /** Vanilla-content shapes, which can either be shaded by the shader pack or drawn in the bypass pass. */
    public static boolean usesBypassOption(String type) {
        return switch (type) {
            case "block", "item", "entity" -> true;
            default -> false;
        };
    }

    public static boolean supportsWireframe(String type) {
        return switch (type) {
            case "box", "sphere", "face_circle", "cylinder", "cone" -> true;
            default -> false;
        };
    }

    private record WireframePart(Shape shape, Vector3f rotation) {}

    private static List<WireframePart> createWireframe(ShapeState state) {
        Color color = new Color(state.color(), true);
        return switch (state.shapeType()) {
            case "box" -> List.of(new WireframePart(ShapeGenerator.generateBoxWireframe().size(size(state))
                    .edgeWidth(state.lineWidth()).color(color).seeThrough(state.seeThrough())
                    .build(Shape.RenderingType.BATCH), new Vector3f()));
            case "face_circle" -> List.of(new WireframePart(ring(state, color), new Vector3f()));
            case "sphere" -> List.of(new WireframePart(ring(state, color), new Vector3f()),
                    new WireframePart(ring(state, color), new Vector3f(90, 0, 0)),
                    new WireframePart(ring(state, color), new Vector3f(0, 0, 90)));
            case "cylinder" -> List.of(new WireframePart(ShapeGenerator.generateCylinderWireframe()
                    .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                    .width(state.lineWidth()).color(color).seeThrough(state.seeThrough())
                    .build(Shape.RenderingType.BATCH), new Vector3f()));
            case "cone" -> List.of(new WireframePart(ShapeGenerator.generateConeWireframe()
                    .radius((float) state.sizeX() / 2).height((float) state.sizeY()).segments(state.segments())
                    .width(state.lineWidth()).color(color).seeThrough(state.seeThrough())
                    .build(Shape.RenderingType.BATCH), new Vector3f()));
            default -> throw new IllegalArgumentException("No wireframe for " + state.shapeType());
        };
    }

    private static Shape ring(ShapeState state, Color color) {
        return ShapeGenerator.generateLineCircle().radius((float) state.sizeX() / 2).segments(state.segments())
                .lineWidth(state.lineWidth()).color(color).seeThrough(state.seeThrough())
                .build(Shape.RenderingType.BATCH);
    }

    private static Identifier wireframeId(String shapeId) {
        Identifier id = Identifier.parse(shapeId);
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "wireframe/" + id.getPath());
    }

    private static Identifier wireframePartId(String shapeId, int index) {
        Identifier root = wireframeId(shapeId);
        return Identifier.fromNamespaceAndPath(root.getNamespace(), root.getPath() + "/" + index);
    }

    private static void syncWireframe(Shape shape, ShapeState state, ShapeState previous) {
        WireframeSettings settings = WireframeSettings.orDefault(state.wireframe());
        List<WireframePart> parts = WIREFRAMES.get(state.shapeId());
        boolean wanted = settings.enabled() && supportsWireframe(state.shapeType());
        if (parts != null && (!wanted || parts.getFirst().shape().parent != shape
                || !state.shapeType().equals(WIREFRAME_TYPES.get(state.shapeId()))
                || parts.getFirst().shape() instanceof BoxWireframeShape box
                        && box.edgeWidth != Math.max(0.1f, state.lineWidth()))) {
            removeWireframe(state.shapeId());
            parts = null;
        }
        if (!wanted) return;
        if (parts == null) {
            parts = createWireframe(state);
            for (int i = 0; i < parts.size(); i++) {
                shape.addChild(parts.get(i).shape());
                ShapeManagers.addShape(wireframePartId(state.shapeId(), i), parts.get(i).shape());
            }
            WIREFRAMES.put(state.shapeId(), parts);
            WIREFRAME_TYPES.put(state.shapeId(), state.shapeType());
        } else if (previous != null && previous.seeThrough() != state.seeThrough()) {
            ShapeManagers.removeShapes(wireframeId(state.shapeId()));
            for (int i = 0; i < parts.size(); i++) {
                parts.get(i).shape().seeThrough = state.seeThrough();
                ShapeManagers.addShape(wireframePartId(state.shapeId(), i), parts.get(i).shape());
            }
        }
        Color color = new Color(settings.separateColor() ? settings.color() : state.color(), true);
        for (WireframePart part : parts) {
            Shape wireframe = part.shape();
            wireframe.seeThrough = state.seeThrough();
            wireframe.forceSetWorldPosition(Vec3.ZERO);
            wireframe.forceSetWorldRotation(new Vector3f(part.rotation()));
            wireframe.forceSetWorldScale(new Vec3(1, 1, 1));
            updateGeometry(wireframe, state);
            wireframe.syncLastToTarget();
            wireframe.setBaseColor(color);
            if (wireframe instanceof CylinderShape cylinder) cylinder.color = color;
            if (state.visible()) wireframe.enable(); else wireframe.disable();
        }
    }

    private static void removeWireframe(String shapeId) {
        List<WireframePart> parts = WIREFRAMES.remove(shapeId);
        WIREFRAME_TYPES.remove(shapeId);
        if (parts == null) return;
        ShapeManagers.removeShapes(wireframeId(shapeId));
        for (WireframePart part : parts) {
            part.shape().setParent(null);
            part.shape().discard();
        }
    }

    private static void ensureCustomManager(Shape shape) {
        if (shape instanceof FontTextShape)
            VertexBuilderGetter.registerEmptyShapeBuilder(FontTextShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        else if (shape instanceof ImageShape)
            VertexBuilderGetter.registerEmptyShapeBuilder(ImageShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        else if (shape instanceof VideoShape)
            VertexBuilderGetter.registerEmptyShapeBuilder(VideoShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        else if (shape instanceof AreaShape)
            VertexBuilderGetter.registerEmptyShapeBuilder(AreaShape.class, ShapeManagers.NON_SHAPE_OBJECTS);
        else if (shape instanceof ArrowShape)
            VertexBuilderGetter.registerShapeBuilder(ArrowShape.class, ShapeManagers.TRIANGLES_SHAPE_MANAGER);
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

    private static Identifier objModelId(String value) {
        Identifier id = value == null ? null : Identifier.tryParse(value);
        return id == null ? Identifier.fromNamespaceAndPath("ryansrenderingkit", "models/monkey.obj") : id;
    }

    public static BlockState blockState(String blockId, Map<String, String> properties) {
        Identifier id = Identifier.tryParse(blockId);
        BlockState result = (id == null ? Blocks.STONE : BuiltInRegistries.BLOCK.getValue(id)).defaultBlockState();
        Map<String, String> values = properties == null ? Map.of() : properties;
        for (Property<?> property : result.getProperties()) {
            String value = values.get(property.getName());
            if (value != null) result = setProperty(result, property, value);
        }
        return result;
    }

    public static List<String> blockIds() {
        if (blockIds == null) blockIds = BuiltInRegistries.BLOCK.keySet().stream()
                .map(Identifier::toString).sorted().toList();
        return blockIds;
    }

    public static List<String> itemIds() {
        if (itemIds == null) itemIds = BuiltInRegistries.ITEM.keySet().stream()
                .map(Identifier::toString).sorted().toList();
        return itemIds;
    }

    public static List<String> entityIds() {
        if (entityIds == null) entityIds = BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue().canSummon())
                .map(entry -> entry.getKey().identifier().toString()).sorted().toList();
        return entityIds;
    }

    private static Entity entity(ShapeState state) {
        var level = Minecraft.getInstance().level;
        EntityType<?> fallback = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
        Identifier id = Identifier.tryParse(state.model());
        EntityType<?> type = id == null ? fallback : BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity entity = level == null ? null : type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null && level != null) entity = fallback.create(level, EntitySpawnReason.COMMAND);
        if (entity != null) entity.setId(nextEntityId--);
        return entity;
    }

    private static int nextEntityId = -1_000_000;

    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static net.minecraft.world.item.Item item(ShapeState state) {
        Identifier id = Identifier.tryParse(state.model());
        return id == null ? Items.DIAMOND : BuiltInRegistries.ITEM.getValue(id);
    }

    private static void applyParent(Shape shape, String parentId) {
        Shape parent = parentId == null || parentId.isEmpty() ? null : SHAPES.get(parentId);
        if (parent == shape || createsCycle(shape, parent)) parent = null;
        if (shape.parent == parent) return;
        if (parent == null) shape.setParent(null); else parent.addChild(shape);
    }

    public static void convertToNewParent(ShapeState state, String newParentId,
            float[] position, float[] rotation, float[] scale) {
        Matrix4f oldWorld = worldTransformOrIdentity(state.parentShapeId())
                .mul(localTransform(position[0], position[1], position[2],
                        rotation[0], rotation[1], rotation[2], scale[0], scale[1], scale[2]));
        Matrix4f newLocal = worldTransformOrIdentity(newParentId).invert().mul(oldWorld);

        Vector3f newPosition = newLocal.getTranslation(new Vector3f());
        Vector3f newScale = newLocal.getScale(new Vector3f());
        Vector3f euler = newLocal.getNormalizedRotation(new Quaternionf()).getEulerAnglesXYZ(new Vector3f());

        position[0] = newPosition.x; position[1] = newPosition.y; position[2] = newPosition.z;
        scale[0] = newScale.x; scale[1] = newScale.y; scale[2] = newScale.z;
        rotation[0] = (float) Math.toDegrees(euler.x);
        rotation[1] = (float) Math.toDegrees(euler.y);
        rotation[2] = (float) Math.toDegrees(euler.z);
    }

     public static Matrix4f worldTransformOrIdentity(String shapeId) {
        return worldTransform(shapeId, new java.util.HashSet<>());
    }

    private static Matrix4f worldTransform(String shapeId, java.util.Set<String> visited) {
        if (shapeId == null || shapeId.isEmpty() || !visited.add(shapeId)) return new Matrix4f();
        ShapeState state = LAST_STATES.get(shapeId);
        if (state == null) return new Matrix4f();
        Matrix4f local = localTransform(state.x(), state.y(), state.z(),
                state.pitch(), state.yaw(), state.roll(), state.scaleX(), state.scaleY(), state.scaleZ());
        return worldTransform(state.parentShapeId(), visited).mul(local);
    }

    private static Matrix4f localTransform(double x, double y, double z, float pitch, float yaw, float roll,
            double scaleX, double scaleY, double scaleZ) {
        return new Matrix4f()
                .translate((float) x, (float) y, (float) z)
                .rotate(new Quaternionf().rotateXYZ((float) Math.toRadians(pitch),
                        (float) Math.toRadians(yaw), (float) Math.toRadians(roll)))
                .scale((float) scaleX, (float) scaleY, (float) scaleZ);
    }

    private static boolean createsCycle(Shape shape, Shape parent) {
        for (Shape current = parent; current != null; current = current.parent) {
            if (current == shape) return true;
        }
        return false;
    }

    public static void fixSeeThroughPipelines() {
        if (fixedSeeThroughPipelines || BuilderManagers.LINES_BUILDER_MANAGER == null
                || BuilderManagers.LINE_STRIP_BUILDER_MANAGER == null
                || BuilderManagers.TRIANGLES_BUILDER_MANAGER == null) return;
        replacePipelines(BuilderManagers.LINES_BUILDER_MANAGER, "lines_translucent", RenderPipelines.LINES_SNIPPET);
        replacePipelines(BuilderManagers.LINE_STRIP_BUILDER_MANAGER, "line_strip_translucent", RenderPipelines.LINES_SNIPPET);
        replacePipelines(BuilderManagers.TRIANGLES_BUILDER_MANAGER, "triangles_translucent", RenderPipelines.DEBUG_FILLED_SNIPPET);
        fixedSeeThroughPipelines = true;
    }

    private static void replacePipelines(BuilderManager manager, String name, RenderPipeline.Snippet snippet) {
        RenderMethod old = manager.renderMethod;
        RenderPipeline normalPipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("vector3", name))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withCull(old.cullFace())
                .withVertexBinding(0, old.format())
                .withPrimitiveTopology(old.mode())
                .build());
        RenderPipeline seeThroughPipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("vector3", name + "_see_through"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))

                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
                .withCull(old.cullFace())
                .withVertexBinding(0, old.format())
                .withPrimitiveTopology(old.mode())
                .build());
        IrisCompat.Program irisProgram = old.mode() == PrimitiveTopology.TRIANGLES
                ? IrisCompat.Program.PARTICLES_TRANSLUCENT : IrisCompat.Program.LINES;
        IrisCompat.assignPipeline(normalPipeline, irisProgram);
        IrisCompat.assignPipeline(seeThroughPipeline, irisProgram);

        RenderSetup.RenderSetupBuilder normalSetup = RenderSetup.builder(normalPipeline);
        RenderSetup.RenderSetupBuilder seeThroughSetup = RenderSetup.builder(seeThroughPipeline);
        if (old.mode() == com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES) {
            normalSetup.sortOnUpload();
            seeThroughSetup.sortOnUpload();
        }
        RenderType normal = RenderType.create(name, normalSetup.createRenderSetup());
        RenderType seeThrough = RenderType.create(name + "_see_through", seeThroughSetup.createRenderSetup());
        manager.renderMethod = new RenderMethod(seeThrough, normal,
                old.mode(), old.format(), old.cullFace());
    }

    private static RenderPipeline imagePipeline;
    private static RenderPipeline imageSeeThroughPipeline;


    public static RenderType imageType(Identifier textureId, boolean seeThrough) {
        if (imagePipeline == null) {
            imagePipeline = registerImagePipeline("image_translucent", DepthStencilState.DEFAULT);
            imageSeeThroughPipeline = registerImagePipeline("image_translucent_see_through",
                    new DepthStencilState(CompareOp.ALWAYS_PASS, true));
        }
        RenderSetup setup = RenderSetup.builder(seeThrough ? imageSeeThroughPipeline : imagePipeline)
                .withTexture("Sampler0", textureId)
                .sortOnUpload()
                .createRenderSetup();
        return RenderType.create(seeThrough ? "vector3_image_see_through" : "vector3_image", setup);
    }

    private static RenderPipeline registerImagePipeline(String name, DepthStencilState depth) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("vector3", name))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(depth)
                .withCull(true)
                .build());
        IrisCompat.assignPipeline(pipeline, IrisCompat.Program.ENTITIES_TRANSLUCENT);
        return pipeline;
    }
}
