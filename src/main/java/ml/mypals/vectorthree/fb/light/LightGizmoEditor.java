package ml.mypals.vectorthree.fb.light;

import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.utils.InputHelper;
import imgui.moulberry90.ImGui;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.line.LineShape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shape.round.LineCircleShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.core.light.Light;
import ml.mypals.vectorthree.core.port.Ports;
import ml.mypals.vectorthree.fb.camera.ViewportPick;
import ml.mypals.vectorthree.fb.custom.CustomKeyframe;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeChange;
import ml.mypals.vectorthree.fb.expression.ExpressionBindings;
import ml.mypals.vectorthree.fb.shape.GizmoMode;
import ml.mypals.vectorthree.fb.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.mc.light.LightRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class LightGizmoEditor {
    private enum Kind { X, Y, Z, YAW, PITCH, RADIUS, WIDTH, HEIGHT, REACH, INNER, OUTER }
    private record Handle(Kind kind, ObjModelShape shape, Color color) {}

    private static final Color X_COLOR = new Color(255, 55, 55, 230);
    private static final Color Y_COLOR = new Color(55, 255, 55, 230);
    private static final Color Z_COLOR = new Color(70, 100, 255, 230);
    private static final Color DIRECTION_COLOR = new Color(255, 210, 60, 230);
    private static final Color SIZE_COLOR = new Color(60, 220, 255, 230);
    private static final Color INNER_COLOR = new Color(255, 160, 60, 230);
    private static final Color OUTLINE_COLOR = new Color(255, 235, 150, 180);

    private final String session = UUID.randomUUID().toString();
    private final List<Handle> handles = new ArrayList<>();
    private final List<LineShape> edges = new ArrayList<>();
    private LineCircleShape ring;
    private LineShape directionLine;
    private ObjModelShape centerMarker;
    private CustomKeyframe<Light> keyframe;
    private KeyframeTrack track;
    private Consumer<Light> commit = light -> {};
    private Handle hovered, dragging;
    private Light preview, dragStart;
    private Vec3 dragAxis, dragOrigin;
    private double dragParameter;

    public boolean isDragging() { return dragging != null; }
    public boolean isHovering() { return hovered != null || dragging != null; }

    public void select(CustomKeyframe<Light> keyframe, KeyframeTrack track, Consumer<Light> commit) {
        this.commit = commit;
        this.track = track;
        if (this.keyframe == keyframe) return;
        this.keyframe = keyframe;
        preview = null;
        dragging = null;
        ensureShapes();
        layout(evaluated());
    }

    public void clearSelection() {
        if (!isDragging() && keyframe != null) clear();
    }

    public void clear() {
        LightRenderer.clearPreview();
        for (Handle handle : handles) handle.shape().discard();
        for (LineShape edge : edges) edge.discard();
        if (ring != null) ring.discard();
        if (directionLine != null) directionLine.discard();
        if (centerMarker != null) centerMarker.discard();
        handles.clear();
        edges.clear();
        ShapeManagers.removeShapes(Mod.id("light_gizmo/" + session));
        ring = null;
        directionLine = null;
        centerMarker = null;
        keyframe = null;
        track = null;
        hovered = dragging = null;
        preview = null;
        commit = light -> {};
    }

    public void frame() {
        if (!ReplayUI.isActive() || keyframe == null) return;
        if (dragging != null && ReplayUI.imguiWindower.isGrabbed()) ReplayUI.imguiWindower.ungrab();
        if (dragging != null && !ImGui.isMouseDown(1)) {
            if (preview != null) {
                Light replacement = rebase(keyframe.value.sanitized(), dragStart, preview, dragging.kind());
                if (!replacement.equals(keyframe.value)) commit.accept(replacement);
            }
            LightRenderer.clearPreview();
            preview = null;
            dragging = null;
            updateColors();
            return;
        }

        Light evaluated = evaluated();
        Light light = preview != null ? preview : evaluated;
        ensureShapes();
        layout(light);
        Vec3 look = ReplayUI.getMouseLookVector();
        if (look == null && dragging != null) look = ShapeGizmoEditor.unboundedMouseLookVector();
        if (look == null) return;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        RayModelIntersection.Ray ray = new RayModelIntersection.Ray(camera.position(), look);
        if (dragging == null) {
            setHovered(ShapeGizmoEditor.mouseInViewport() ? pick(ray) : null);
            if (ShapeGizmoEditor.mouseInViewport() && ImGui.isMouseClicked(2)) {
                ViewportPick.Hit hit = ViewportPick.pick(ray.origin, ray.direction);
                if (hit != null) {
                    Vec3 point = hit.entity() != null ? hit.entity().getBoundingBox().getCenter()
                            : InputHelper.isCtrlDownRaw() && hit.block() != null
                            ? Vec3.atCenterOf(hit.block().getBlockPos().relative(hit.block().getDirection()))
                            : hit.location();
                    if (!point.equals(light.position())) {
                        Light edited = replace(light, point, light.direction(), light.radius(), light.areaWidth(),
                                light.areaHeight(), light.areaReach(), light.innerAngle(), light.outerAngle());
                        commit.accept(rebase(keyframe.value.sanitized(), light, edited, Kind.X));
                    }
                }
            }
            if (ImGui.isMouseClicked(1) && hovered != null) {
                ReplayUI.imguiWindower.ungrab();
                dragging = hovered;
                dragStart = light;
                dragAxis = axis(light, hovered.kind());
                dragOrigin = handlePosition(light, hovered.kind());
                dragParameter = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis);
                updateColors();
            }
        }
        if (dragging != null && ImGui.isMouseDown(1)) {
            double delta = ShapeGizmoEditor.axisParameter(ray, dragOrigin, dragAxis) - dragParameter;
            Light next = drag(delta);
            if (next != null) {
                preview = next;
                LightRenderer.preview(evaluated, next);
                layout(next);
            }
        }
    }

    private void ensureShapes() {
        if (ring != null) return;
        ring = ShapeGenerator.generateLineCircle().radius(1).segments(64).lineWidth(2.5f)
                .color(OUTLINE_COLOR).seeThrough(true).build(Shape.RenderingType.BATCH);
        directionLine = line();
        centerMarker = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                ShapeGizmoEditor.CENTER_MODEL, Vec3.ZERO, OUTLINE_COLOR, true);
        add("ring", ring);
        add("direction", directionLine);
        add("center", centerMarker);
        for (int i = 0; i < 12; i++) {
            LineShape edge = line();
            edges.add(edge);
            add("edge/" + i, edge);
        }
        addHandle(Kind.X, X_COLOR);
        addHandle(Kind.Y, Y_COLOR);
        addHandle(Kind.Z, Z_COLOR);
        addHandle(Kind.YAW, DIRECTION_COLOR);
        addHandle(Kind.PITCH, SIZE_COLOR);
        addHandle(Kind.RADIUS, SIZE_COLOR);
        addHandle(Kind.WIDTH, X_COLOR);
        addHandle(Kind.HEIGHT, Y_COLOR);
        addHandle(Kind.REACH, DIRECTION_COLOR);
        addHandle(Kind.INNER, INNER_COLOR);
        addHandle(Kind.OUTER, SIZE_COLOR);
    }

    private static LineShape line() {
        return ShapeGenerator.generateLine().start(Vec3.ZERO).end(new Vec3(0, 1, 0))
                .lineWidth(2.5f).color(OUTLINE_COLOR).seeThrough(true).build(Shape.RenderingType.BATCH);
    }

    private void add(String name, Shape shape) {
        ShapeManagers.addShape(Mod.id("light_gizmo/" + session + "/" + name), shape);
    }

    private void addHandle(Kind kind, Color color) {
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                ShapeGizmoEditor.MOVE_MODEL, Vec3.ZERO, color, true);
        add("handle/" + kind.name().toLowerCase(java.util.Locale.ROOT), shape);
        handles.add(new Handle(kind, shape, color));
    }

    private void layout(Light light) {
        Vec3 center = light.position();
        Vec3 direction = light.direction();
        double range = light.radius();
        double size = ShapeGizmoEditor.gizmoScale(center);
        centerMarker.forceSetWorldPosition(center);
        centerMarker.forceSetWorldScale(new Vec3(size * 0.5, size * 0.5, size * 0.5));
        directionLine.forceSetStart(center);
        directionLine.forceSetEnd(center.add(direction.scale(light.type() == Light.Type.AREA
                ? light.areaReach() : light.type() == Light.Type.SPOT ? range : 0)));
        if (light.type() == Light.Type.POINT) directionLine.disable();
        else directionLine.enable();

        if (light.type() == Light.Type.SPOT) {
            Vec3 tip = center.add(direction.scale(range));
            ring.enable();
            ring.forceSetWorldPosition(tip);
            ring.forceSetWorldRotation(euler(direction));
            ring.forceSetRadius((float) (range * Math.tan(Math.toRadians(light.outerAngle()))));
            Vec3 right = right(light), up = up(light);
            double spread = range * Math.tan(Math.toRadians(light.outerAngle()));
            Vec3[] rim = {tip.add(right.scale(spread)), tip.add(up.scale(spread)),
                    tip.subtract(right.scale(spread)), tip.subtract(up.scale(spread))};
            for (int i = 0; i < 4; i++) showLine(edges.get(i), center, rim[i]);
            for (int i = 4; i < edges.size(); i++) edges.get(i).disable();
        } else if (light.type() == Light.Type.AREA) {
            ring.enable();
            ring.forceSetWorldPosition(center);
            ring.forceSetWorldRotation(euler(direction));
            ring.forceSetRadius(light.radius());
            Vec3 r = right(light).scale(light.areaWidth() * 0.5);
            Vec3 u = up(light).scale(light.areaHeight() * 0.5);
            Vec3[] corners = {center.add(r).add(u), center.subtract(r).add(u),
                    center.subtract(r).subtract(u), center.add(r).subtract(u)};
            Vec3 offset = direction.scale(light.areaReach());
            for (int i = 0; i < 4; i++) {
                showLine(edges.get(i), corners[i], corners[(i + 1) % 4]);
                showLine(edges.get(i + 4), corners[i], corners[i].add(offset));
                showLine(edges.get(i + 8), corners[i].add(offset), corners[(i + 1) % 4].add(offset));
            }
        } else {
            ring.enable();
            ring.forceSetWorldPosition(center);
            ring.forceSetWorldRotation(new Vector3f());
            ring.forceSetRadius(light.radius());
            for (LineShape edge : edges) edge.disable();
        }
        for (Handle handle : handles) {
            if (!shown(light, handle.kind())) {
                handle.shape().disable();
                continue;
            }
            handle.shape().enable();
            Vec3 position = handlePosition(light, handle.kind());
            double scale = ShapeGizmoEditor.gizmoScale(position);
            handle.shape().forceSetWorldPosition(position);
            handle.shape().forceSetWorldScale(new Vec3(scale, scale, scale));
            handle.shape().forceSetWorldRotation(euler(axis(light, handle.kind())));
        }
    }

    private static void showLine(LineShape line, Vec3 start, Vec3 end) {
        line.enable();
        line.forceSetStart(start);
        line.forceSetEnd(end);
    }

    private boolean shown(Light light, Kind kind) {
        if (dragging != null) return dragging.kind() == kind;
        return switch (GizmoMode.current()) {
            case MOVE -> kind == Kind.X || kind == Kind.Y || kind == Kind.Z;
            case ROTATE -> light.type() != Light.Type.POINT && (kind == Kind.YAW || kind == Kind.PITCH);
            case SCALE -> kind == Kind.RADIUS || light.type() == Light.Type.AREA
                    && (kind == Kind.WIDTH || kind == Kind.HEIGHT || kind == Kind.REACH);
            case GEOMETRY -> switch (light.type()) {
                case POINT -> kind == Kind.RADIUS;
                case AREA -> kind == Kind.WIDTH || kind == Kind.HEIGHT || kind == Kind.REACH;
                case SPOT -> kind == Kind.INNER || kind == Kind.OUTER || kind == Kind.RADIUS;
            };
        };
    }

    private static Vec3 handlePosition(Light light, Kind kind) {
        Vec3 center = light.position();
        double size = ShapeGizmoEditor.gizmoScale(center);
        Vec3 direction = light.direction();
        return switch (kind) {
            case X, Y, Z -> center;
            case YAW -> center.add(direction.scale(size * 2)).add(yawTangent(light).scale(size * 0.4));
            case PITCH -> center.add(direction.scale(size * 2)).add(pitchTangent(light).scale(size * 0.4));
            case RADIUS -> center.add(light.type() == Light.Type.SPOT
                    ? direction.scale(light.radius()) : new Vec3(light.radius(), 0, 0));
            case WIDTH -> center.add(right(light).scale(light.areaWidth() * 0.5));
            case HEIGHT -> center.add(up(light).scale(light.areaHeight() * 0.5));
            case REACH -> center.add(direction.scale(light.areaReach()));
            case INNER -> center.add(direction.scale(light.radius()))
                    .add(up(light).scale(light.radius() * Math.tan(Math.toRadians(light.innerAngle()))));
            case OUTER -> center.add(direction.scale(light.radius()))
                    .add(right(light).scale(light.radius() * Math.tan(Math.toRadians(light.outerAngle()))));
        };
    }

    private static Vec3 axis(Light light, Kind kind) {
        return switch (kind) {
            case X -> new Vec3(1, 0, 0);
            case RADIUS -> light.type() == Light.Type.SPOT ? light.direction() : new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
            case YAW -> yawTangent(light);
            case PITCH -> pitchTangent(light);
            case WIDTH, OUTER -> right(light);
            case HEIGHT, INNER -> up(light);
            case REACH -> light.direction();
        };
    }

    private Light drag(double delta) {
        Light start = dragStart;
        Vec3 position = start.position(), direction = start.direction();
        float radius = start.radius(), width = start.areaWidth(), height = start.areaHeight(), reach = start.areaReach();
        float inner = start.innerAngle(), outer = start.outerAngle();
        boolean snap = InputHelper.isCtrlDownRaw();
        switch (dragging.kind()) {
            case X, Y, Z -> position = position.add(dragAxis.scale(delta));
            case YAW, PITCH -> {
                double yaw = Math.atan2(-direction.x, direction.z);
                double pitch = Math.asin(Math.clamp(-direction.y, -1, 1));
                double change = delta / Math.max(ShapeGizmoEditor.gizmoScale(position) * 2, 0.01);
                if (dragging.kind() == Kind.YAW) yaw += change;
                else pitch = Math.clamp(pitch + change, -Math.PI / 2, Math.PI / 2);
                if (snap) {
                    if (dragging.kind() == Kind.YAW) yaw = Math.toRadians(Math.round(Math.toDegrees(yaw) / 15) * 15);
                    else pitch = Math.toRadians(Math.round(Math.toDegrees(pitch) / 15) * 15);
                }
                direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                        Math.cos(yaw) * Math.cos(pitch));
            }
            case RADIUS -> radius = (float) (radius + delta);
            case WIDTH -> width = (float) (width + delta * 2);
            case HEIGHT -> height = (float) (height + delta * 2);
            case REACH -> reach = (float) (reach + delta);
            case INNER -> inner = (float) Math.toDegrees(Math.atan(Math.max(0,
                    start.radius() * Math.tan(Math.toRadians(inner)) + delta) / start.radius()));
            case OUTER -> outer = (float) Math.toDegrees(Math.atan(Math.max(0,
                    start.radius() * Math.tan(Math.toRadians(outer)) + delta) / start.radius()));
        }
        if (snap) {
            switch (dragging.kind()) {
                case X -> position = new Vec3(Math.round(position.x * 2) / 2.0, position.y, position.z);
                case Y -> position = new Vec3(position.x, Math.round(position.y * 2) / 2.0, position.z);
                case Z -> position = new Vec3(position.x, position.y, Math.round(position.z * 2) / 2.0);
                case RADIUS -> radius = Math.round(radius * 2) / 2f;
                case WIDTH -> width = Math.round(width * 2) / 2f;
                case HEIGHT -> height = Math.round(height * 2) / 2f;
                case REACH -> reach = Math.round(reach * 2) / 2f;
                case INNER -> inner = Math.round(inner / 5) * 5;
                case OUTER -> outer = Math.round(outer / 5) * 5;
                default -> {}
            }
        }
        return replace(start, position, direction, radius, width, height, reach, inner, outer);
    }

    private static Light replace(Light start, Vec3 position, Vec3 direction, float radius,
            float width, float height, float reach, float inner, float outer) {
        return new Light(position, start.red(), start.green(), start.blue(), start.intensity(), radius,
                start.volume(), start.shadow(), start.type(), direction, width, height, inner, outer, reach).sanitized();
    }

    private Light evaluated() {
        if (track != null && ExpressionBindings.any(track)
                && track.createKeyframeChange((float) Ports.clock().effectTick(), null)
                instanceof CustomKeyframeChange change && change.value() instanceof Light light) {
            return light.sanitized();
        }
        return keyframe.value.sanitized();
    }

    private static Light rebase(Light stored, Light evaluated, Light edited, Kind kind) {
        Vec3 position = stored.position(), direction = stored.direction();
        float radius = stored.radius(), width = stored.areaWidth(), height = stored.areaHeight();
        float reach = stored.areaReach(), inner = stored.innerAngle(), outer = stored.outerAngle();
        switch (kind) {
            case X, Y, Z -> position = position.add(edited.position().subtract(evaluated.position()));
            case YAW, PITCH -> {
                double yaw = Math.atan2(-direction.x, direction.z)
                        + Math.atan2(-edited.direction().x, edited.direction().z)
                        - Math.atan2(-evaluated.direction().x, evaluated.direction().z);
                double pitch = Math.asin(Math.clamp(-direction.y, -1, 1))
                        + Math.asin(Math.clamp(-edited.direction().y, -1, 1))
                        - Math.asin(Math.clamp(-evaluated.direction().y, -1, 1));
                pitch = Math.clamp(pitch, -Math.PI / 2, Math.PI / 2);
                direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                        Math.cos(yaw) * Math.cos(pitch));
            }
            case RADIUS -> radius += edited.radius() - evaluated.radius();
            case WIDTH -> width += edited.areaWidth() - evaluated.areaWidth();
            case HEIGHT -> height += edited.areaHeight() - evaluated.areaHeight();
            case REACH -> reach += edited.areaReach() - evaluated.areaReach();
            case INNER -> inner += edited.innerAngle() - evaluated.innerAngle();
            case OUTER -> outer += edited.outerAngle() - evaluated.outerAngle();
        }
        return replace(stored, position, direction, radius, width, height, reach, inner, outer);
    }

    private static Vec3 right(Light light) {
        Vec3 right = light.direction().cross(new Vec3(0, 1, 0));
        return right.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : right.normalize();
    }

    private static Vec3 up(Light light) { return right(light).cross(light.direction()).normalize(); }

    private static Vec3 yawTangent(Light light) {
        double yaw = Math.atan2(-light.direction().x, light.direction().z);
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    private static Vec3 pitchTangent(Light light) {
        double yaw = Math.atan2(-light.direction().x, light.direction().z);
        double pitch = Math.asin(Math.clamp(-light.direction().y, -1, 1));
        return new Vec3(Math.sin(yaw) * Math.sin(pitch), -Math.cos(pitch),
                -Math.cos(yaw) * Math.sin(pitch));
    }

    private static Vector3f euler(Vec3 direction) {
        Vector3f angles = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction.toVector3f())
                .getEulerAnglesXYZ(new Vector3f());
        return angles.mul((float) (180 / Math.PI));
    }

    private Handle pick(RayModelIntersection.Ray ray) {
        Handle best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Handle handle : handles) {
            if (!handle.shape().enabled()) continue;
            RayModelIntersection.HitResult hit = RayModelIntersection.rayIntersectsModel(
                    ray, handle.shape().getModel(false), handle.shape().indexBuffer);
            if (hit.hit && hit.distance < distance) {
                best = handle;
                distance = hit.distance;
            }
        }
        return best;
    }

    private void setHovered(Handle handle) {
        if (hovered == handle) return;
        hovered = handle;
        updateColors();
    }

    private void updateColors() {
        for (Handle handle : handles) {
            handle.shape().setBaseColor(handle == dragging ? ShapeGizmoEditor.ACTIVE_COLOR
                    : handle == hovered ? ShapeGizmoEditor.HOVER_COLOR : handle.color());
        }
    }
}
