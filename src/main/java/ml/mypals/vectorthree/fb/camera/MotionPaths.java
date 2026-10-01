package ml.mypals.vectorthree.fb.camera;

import ml.mypals.vectorthree.fb.editor.EditorInput;
import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.model.ObjModelShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.core.shape.ShapeState;
import ml.mypals.vectorthree.fb.Editors;
import ml.mypals.vectorthree.fb.editor.PropertiesWindow;
import ml.mypals.vectorthree.fb.shape.ShapeGizmoEditor;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframe;
import ml.mypals.vectorthree.fb.timeline.Timeline;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Draws every camera keyframe track and every shape's keyframes as a path in the viewport: a line through samples
 * taken every N ticks, small dots at the samples and a big dot at each keyframe. Right-clicking a keyframe dot selects
 * that keyframe, which opens its gizmo and the Properties window.
 */
public final class MotionPaths {
    private static final Color CAMERA_COLOR = new Color(80, 200, 255, 230);
    private static final Color SHAPE_COLOR = new Color(255, 170, 60, 230);
    private static final int MAX_SAMPLES = 1500;
    private static final int MAX_DOTS = 300;
    private static final float LINE_WIDTH = 2f;

    private record Dot(Vec3 position, boolean keyframe, Color color, int track, int tick, ObjModelShape shape) {}

    private static boolean show = true, cameras = true, shapes = true;
    private static final int[] INTERVAL = {5};
    private static final String SESSION = UUID.randomUUID().toString();
    private static final List<Dot> dots = new ArrayList<>();
    private static final List<Shape> lines = new ArrayList<>();
    private static int signature;
    private static Dot hovered;

    private MotionPaths() {}

    public static boolean isHovering() {
        return hovered != null;
    }

    public static void renderMenu() {
        if (!ImGui.beginMenu(I18n.get("vector3.motion_path.title"))) return;
        if (ImGui.menuItem(I18n.get("vector3.motion_path.show"), "", show)) show = !show;
        if (ImGui.menuItem(I18n.get("vector3.motion_path.cameras"), "", cameras)) cameras = !cameras;
        if (ImGui.menuItem(I18n.get("vector3.motion_path.shapes"), "", shapes)) shapes = !shapes;
        ImGui.setNextItemWidth(120);
        ImGui.sliderInt(I18n.get("vector3.motion_path.interval"), INTERVAL, 1, 40);
        ImGui.endMenu();
    }

    public static void clear() {
        for (Dot dot : dots) dot.shape().discard();
        dots.clear();
        for (Shape line : lines) line.discard();
        lines.clear();
        ShapeManagers.removeShapes(Mod.id("motion_path/" + SESSION));
        hovered = null;
        signature = 0;
    }

    public static void frame() {
        EditorScene scene = Timeline.scene();
        if (!show || scene == null || !ReplayUI.isActive()) {
            if (!dots.isEmpty() || !lines.isEmpty()) clear();
            return;
        }
        int now = Objects.hash(sceneSignature(scene), INTERVAL[0], cameras, shapes);
        if (now != signature) {
            clear();
            signature = now;
            build(scene);
        }
        scale();
        pick();
    }

    private static int sceneSignature(EditorScene scene) {
        int hash = 1;
        for (KeyframeTrack track : scene.keyframeTracks) {
            hash = hash * 31 + (track.enabled ? 1 : 0);
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                Keyframe keyframe = entry.getValue();
                hash = hash * 31 + entry.getKey();
                if (keyframe instanceof CameraKeyframe camera) {
                    hash = hash * 31 + Objects.hash(camera.position.x, camera.position.y, camera.position.z,
                            camera.interpolationType());
                } else if (keyframe instanceof ShapeKeyframe shape) {
                    hash = hash * 31 + shape.value.hashCode();
                }
            }
        }
        return hash;
    }

    private static void build(EditorScene scene) {
        int interval = INTERVAL[0];
        if (cameras) {
            for (int index = 0; index < scene.keyframeTracks.size(); index++) {
                KeyframeTrack track = scene.keyframeTracks.get(index);
                if (!track.enabled || track.keyframesByTick.isEmpty()) continue;
                boolean any = false;
                for (Keyframe keyframe : track.keyframesByTick.values()) any |= keyframe instanceof CameraKeyframe;
                if (any) buildCamera(track, index, interval);
            }
        }
        if (shapes) buildShapes(scene, interval);
    }

    private static Vec3 eye(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        return new Vec3(x, y + (minecraft.player == null ? 1.62 : minecraft.player.getEyeHeight()), z);
    }

    private static void buildCamera(KeyframeTrack track, int trackIndex, int interval) {
        int first = track.keyframesByTick.firstKey(), last = track.keyframesByTick.lastKey();
        int step = Math.max(interval, (last - first) / MAX_SAMPLES + 1);
        List<Vec3> line = new ArrayList<>();
        List<Dot> samples = new ArrayList<>();
        for (int tick = first; tick <= last; tick += step) {
            KeyframeChange change = track.createKeyframeChange(tick, null);
            if (!(change instanceof KeyframeChangeCameraPosition position)) continue;
            Vec3 at = eye(position.position().x, position.position().y, position.position().z);
            line.add(at);
            if (!track.keyframesByTick.containsKey(tick)) samples.add(dot(at, false, CAMERA_COLOR, trackIndex, tick));
        }
        for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
            if (!(entry.getValue() instanceof CameraKeyframe camera)) continue;
            dot(eye(camera.position.x, camera.position.y, camera.position.z), true, CAMERA_COLOR, trackIndex, entry.getKey());
        }
        addLine(line, CAMERA_COLOR);
        trimDots(samples);
    }

    private static void buildShapes(EditorScene scene, int interval) {
        Map<String, TreeMap<Float, ShapeState>> byShape = new HashMap<>();
        Map<String, List<int[]>> where = new HashMap<>();
        for (int index = 0; index < scene.keyframeTracks.size(); index++) {
            KeyframeTrack track = scene.keyframeTracks.get(index);
            if (!track.enabled) continue;
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                if (!(entry.getValue() instanceof ShapeKeyframe shape) || shape.value.screen()) continue;
                byShape.computeIfAbsent(shape.value.shapeId(), id -> new TreeMap<>()).put((float) entry.getKey(), shape.value);
                where.computeIfAbsent(shape.value.shapeId(), id -> new ArrayList<>()).add(new int[]{index, entry.getKey()});
            }
        }
        for (Map.Entry<String, TreeMap<Float, ShapeState>> entry : byShape.entrySet()) {
            TreeMap<Float, ShapeState> states = entry.getValue();
            if (states.size() < 2) continue;
            int first = states.firstKey().intValue(), last = states.lastKey().intValue();
            int step = Math.max(interval, (last - first) / MAX_SAMPLES + 1);
            List<Vec3> line = new ArrayList<>();
            List<Dot> samples = new ArrayList<>();
            for (int tick = first; tick <= last; tick += step) {
                ShapeState state = ShapeState.hermite(states, tick);
                if (state == null) continue;
                Vec3 at = new Vec3(state.x(), state.y(), state.z());
                line.add(at);
                if (!states.containsKey((float) tick)) samples.add(dot(at, false, SHAPE_COLOR, -1, tick));
            }
            for (int[] key : where.get(entry.getKey())) {
                ShapeState state = states.get((float) key[1]);
                dot(new Vec3(state.x(), state.y(), state.z()), true, SHAPE_COLOR, key[0], key[1]);
            }
            addLine(line, SHAPE_COLOR);
            trimDots(samples);
        }
    }

    private static Dot dot(Vec3 at, boolean keyframe, Color color, int track, int tick) {
        ObjModelShape shape = new ObjModelShape(Shape.RenderingType.BATCH, transformer -> {},
                ShapeGizmoEditor.CENTER_MODEL, at, keyframe ? Color.WHITE : color, true);
        ShapeManagers.addShape(Mod.id("motion_path/" + SESSION + "/dot/" + dots.size()), shape);
        Dot dot = new Dot(at, keyframe, color, track, tick, shape);
        dots.add(dot);
        return dot;
    }

    // Sample dots past the cap are dropped, evenly, so a long path is thinned rather than cut off.
    private static void trimDots(List<Dot> samples) {
        int over = dots.size() - MAX_DOTS;
        if (over <= 0 || samples.isEmpty()) return;
        int dropped = 0;
        for (int i = 0; i < samples.size() && dropped < over; i++) {
            if (i % 2 == 0 || samples.size() < over * 2) {
                Dot dot = samples.get(i);
                dot.shape().discard();
                dots.remove(dot);
                dropped++;
            }
        }
    }

    private static void addLine(List<Vec3> points, Color color) {
        if (points.size() < 2) return;
        Shape line = ShapeGenerator.generateStripLine().vertexes(points).lineWidth(LINE_WIDTH).color(color)
                .seeThrough(true).build(Shape.RenderingType.BATCH);
        ShapeManagers.addShape(Mod.id("motion_path/" + SESSION + "/line/" + lines.size()), line);
        lines.add(line);
    }

    private static double radius(Dot dot) {
        return ShapeGizmoEditor.gizmoScale(dot.position()) * (dot.keyframe() ? 0.28 : 0.1);
    }

    private static void scale() {
        for (Dot dot : dots) {
            double size = radius(dot);
            dot.shape().forceSetWorldScale(new Vec3(size, size, size));
            dot.shape().setBaseColor(dot == hovered ? ShapeGizmoEditor.HOVER_COLOR : dot.keyframe() ? Color.WHITE : dot.color());
        }
    }

    private static void pick() {
        hovered = null;
        boolean blocked = Editors.CAMERA_GIZMO.isHovering() || Editors.GIZMO_EDITOR.isHovering()
                || Editors.ORBIT_GIZMO.isHovering() || Editors.POSE_GIZMO.isHovering()
                || Editors.LIGHT_GIZMO.isHovering() || Editors.PREFABS.isHovering()
                || Editors.CAMERA_GIZMO.isDragging() || Editors.GIZMO_EDITOR.isDragging();
        Vec3 direction = ReplayUI.getMouseLookVector();
        if (blocked || direction == null || !ShapeGizmoEditor.mouseInViewport()) return;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 origin = camera.position();
        Vec3 look = direction.normalize();
        double best = Double.POSITIVE_INFINITY;
        for (Dot dot : dots) {
            if (!dot.keyframe() || dot.track() < 0) continue;
            Vec3 toDot = dot.position().subtract(origin);
            double along = toDot.dot(look);
            if (along <= 0 || along >= best) continue;
            double miss = toDot.subtract(look.scale(along)).length();
            if (miss <= radius(dot) * 1.6) {
                hovered = dot;
                best = along;
            }
        }
        if (hovered != null) {
            ImGui.setTooltip(I18n.get("vector3.motion_path.tooltip", hovered.tick()));
            if (ImGui.isMouseClicked(1)) {
                EditorInput.ungrab();
                select(hovered);
            }
        }
    }

    private static void select(Dot dot) {
        EditorScene scene = Timeline.scene();
        if (scene == null || dot.track() >= scene.keyframeTracks.size()) return;
        Timeline.selected().clear();
        IntSet ticks = new IntOpenHashSet();
        ticks.add(dot.tick());
        Timeline.selected().add(new SelectedKeyframes(scene.keyframeTracks.get(dot.track()).keyframeType, dot.track(), ticks));
        Timeline.setEditingTrack(dot.track());
        Timeline.setEditingTick(dot.tick());
        PropertiesWindow.requestFocus();
    }
}
