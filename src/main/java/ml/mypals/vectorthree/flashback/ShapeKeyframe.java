package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapePoint;
import ml.mypals.vectorthree.shape.ShapeTrackEditor;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImInt;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class ShapeKeyframe extends Keyframe {
    public ShapeState state;
    private static @Nullable ShapeTrackEditor editor;

    public ShapeKeyframe(ShapeState state) {
        this.state = state;
    }

    public ShapeKeyframe(ShapeState state, InterpolationType interpolation) {
        this(state);
        interpolationType(interpolation);
    }

    public static void setEditor(@Nullable ShapeTrackEditor editor) {
        ShapeKeyframe.editor = editor;
    }

    @Override public KeyframeType<?> keyframeType() { return ShapeKeyframeType.INSTANCE; }
    @Override public Keyframe copy() { return new ShapeKeyframe(state, interpolationType()); }
    @Override public KeyframeChange createChange() { return new ShapeKeyframeChange(state); }

    @Override
    public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3,
            float t0, float t1, float t2, float t3, float amount) {
        return new ShapeKeyframeChange(((ShapeKeyframe) p1).state
                .interpolate(((ShapeKeyframe) p2).state, amount));
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        TreeMap<Float, Keyframe> sorted = new TreeMap<>(keyframes);
        Map.Entry<Float, Keyframe> floor = sorted.floorEntry(amount);
        Map.Entry<Float, Keyframe> ceil = sorted.ceilingEntry(amount);
        if (floor == null) floor = sorted.firstEntry();
        if (ceil == null) ceil = sorted.lastEntry();
        float span = ceil.getKey() - floor.getKey();
        double local = span == 0 ? 0 : (amount - floor.getKey()) / span;
        return new ShapeKeyframeChange(((ShapeKeyframe) floor.getValue()).state
                .interpolate(((ShapeKeyframe) ceil.getValue()).state, local));
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        String[] selectedType = {state.shapeType()};
        String[] selectedId = {state.shapeId()};
        ShapeTrackRegistry.Definition selectedDefinition = ShapeTrackRegistry.definition(selectedType[0]);
        boolean changed = false;
        ImGui.setNextItemWidth(240);
        if (selectedDefinition != null && ImGui.beginCombo("Shape", selectedDefinition.name())) {
            for (ShapeTrackRegistry.Definition definition : ShapeTrackRegistry.definitions()) {
                if (ImGui.selectable(definition.name(), definition.id().equals(selectedType[0]))) {
                    selectedType[0] = definition.id();
                    changed = true;
                }
            }
            ImGui.endCombo();
        }

        ShapeTrackRegistry.previewHighlight(null);
        ImGui.setNextItemWidth(360);
        if (ImGui.beginCombo("Shape UUID", selectedId[0])) {
            for (String shapeId : ShapeTrackRegistry.shapeIds()) {
                if (ImGui.selectable(shapeId, shapeId.equals(selectedId[0]))) {
                    selectedId[0] = shapeId;
                    String existingType = ShapeTrackRegistry.typeOf(shapeId);
                    if (existingType != null) selectedType[0] = existingType;
                    changed = true;
                }
                if (ImGui.isItemHovered()) ShapeTrackRegistry.previewHighlight(shapeId);
            }
            ImGui.endCombo();
        }

        float[] position = {(float) state.x(), (float) state.y(), (float) state.z()};
        float[] rotation = {state.pitch(), state.yaw(), state.roll()};
        float[] scale = {(float) state.scaleX(), (float) state.scaleY(), (float) state.scaleZ()};
        float[] size = {(float) state.sizeX(), (float) state.sizeY(), (float) state.sizeZ()};
        float[] width = {state.lineWidth()};
        float[] color = {
                ((state.color() >>> 16) & 255) / 255.0f,
                ((state.color() >>> 8) & 255) / 255.0f,
                (state.color() & 255) / 255.0f,
                ((state.color() >>> 24) & 255) / 255.0f
        };
        ImInt segments = new ImInt(state.segments());
        ImBoolean seeThrough = new ImBoolean(state.seeThrough());
        ImBoolean visible = new ImBoolean(state.visible());

        changed |= ImGui.dragFloat3("Position", position, 0.05f);
        changed |= ImGui.dragFloat3("Rotation", rotation, 1.0f);
        changed |= ImGui.dragFloat3("Scale", scale, 0.01f, 0.001f, 1000.0f);
        changed |= ImGui.colorEdit4("Color", color);

        List<ShapePoint> points = new ArrayList<>(state.points() == null ? List.of() : state.points());
        switch (selectedType[0]) {
            case "box" ->
                    changed |= ImGui.dragFloat3("Dimensions", size, 0.05f, 0.001f, 1000.0f);
            case "box_wireframe", "wireframed_box" -> {
                changed |= ImGui.dragFloat3("Dimensions", size, 0.05f, 0.001f, 1000.0f);
                changed |= ImGui.dragFloat("Line Width", width, 0.01f, 0.001f, 100.0f);
            }
            case "sphere", "face_circle", "line_circle" -> {
                float[] radius = {(float) state.sizeX() / 2};
                if (ImGui.dragFloat("Radius", radius, 0.05f, 0.001f, 1000.0f)) {
                    size[0] = radius[0] * 2;
                    changed = true;
                }
                changed |= ImGui.inputInt("Segments", segments);
                if (selectedType[0].equals("line_circle"))
                    changed |= ImGui.dragFloat("Line Width", width, 0.01f, 0.001f, 100.0f);
            }
            case "cylinder", "cylinder_wireframe", "cone", "cone_wireframe" -> {
                float[] radius = {(float) state.sizeX() / 2};
                float[] height = {(float) state.sizeY()};
                if (ImGui.dragFloat("Radius", radius, 0.05f, 0.001f, 1000.0f)) {
                    size[0] = radius[0] * 2;
                    changed = true;
                }
                if (ImGui.dragFloat("Height", height, 0.05f, 0.001f, 1000.0f)) {
                    size[1] = height[0];
                    changed = true;
                }
                changed |= ImGui.inputInt("Segments", segments);
                if (selectedType[0].endsWith("wireframe"))
                    changed |= ImGui.dragFloat("Line Width", width, 0.01f, 0.001f, 100.0f);
            }
            case "line" -> {
                while (points.size() < 2) points.add(new ShapePoint(0, 0, 0));
                changed |= editPoint("Start", points, 0);
                changed |= editPoint("End", points, 1);
                changed |= ImGui.dragFloat("Line Width", width, 0.01f, 0.001f, 100.0f);
            }
            case "line_strip" -> {
                while (points.size() < 2) points.add(new ShapePoint(points.size(), points.size(), points.size()));
                for (int i = 0; i < points.size(); i++) {
                    changed |= editPoint("Point " + (i + 1), points, i);
                    ImGui.sameLine();
                    if (ImGui.button("Remove##point" + i) && points.size() > 2) {
                        points.remove(i--);
                        changed = true;
                    }
                }
                if (ImGui.button("Add Point")) {
                    points.add(points.isEmpty() ? new ShapePoint(0, 0, 0) : points.getLast());
                    changed = true;
                }
                changed |= ImGui.dragFloat("Line Width", width, 0.01f, 0.001f, 100.0f);
            }
        }
        changed |= ImGui.checkbox("See Through", seeThrough);
        changed |= ImGui.checkbox("Visible", visible);

        if (changed) {
            int argb = (Math.round(color[3] * 255) << 24) | (Math.round(color[0] * 255) << 16)
                    | (Math.round(color[1] * 255) << 8) | Math.round(color[2] * 255);
            ShapeState replacement = state.with(position, rotation, scale, size,
                    Math.max(3, segments.get()), Math.max(0.001f, width[0]), argb,
                    points, seeThrough.get(), visible.get())
                    .withIdentity(selectedType[0], selectedId[0]);
            update.accept(keyframe -> ((ShapeKeyframe) keyframe).state = replacement);
        }

        ShapeTrackEditor current = editor;
        if (current != null) {
            current.edit(this, typed -> update.accept(keyframe -> typed.accept((ShapeKeyframe) keyframe)));
        }
    }

    private static boolean editPoint(String label, List<ShapePoint> points, int index) {
        ShapePoint point = points.get(index);
        float[] value = {(float) point.x(), (float) point.y(), (float) point.z()};
        if (!ImGui.dragFloat3(label, value, 0.05f)) return false;
        points.set(index, new ShapePoint(value[0], value[1], value[2]));
        return true;
    }
}
