package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapePoint;
import ml.mypals.vectorthree.shape.ShapeTrackEditor;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.shape.TextSettings;
import ml.mypals.vectorthree.shape.VideoShape;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        return new ShapeKeyframeChange(ShapeState.smooth(state, ((ShapeKeyframe) p1).state,
                ((ShapeKeyframe) p2).state, ((ShapeKeyframe) p3).state,
                t1 - t0, t2 - t0, t3 - t0, amount));
    }

    @Override
    public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float amount) {
        Map<Float, ShapeState> states = new java.util.TreeMap<>();
        keyframes.forEach((tick, keyframe) -> states.put(tick, ((ShapeKeyframe) keyframe).state));
        return new ShapeKeyframeChange(ShapeState.hermite(states, amount));
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

        String[] parentId = {state.parentShapeId() == null ? "" : state.parentShapeId()};
        ImGui.setNextItemWidth(360);
        if (ImGui.beginCombo("Parent Shape UUID", parentId[0].isEmpty() ? "None" : parentId[0])) {
            if (ImGui.selectable("None", parentId[0].isEmpty())) {
                parentId[0] = "";
                changed = true;
            }
            for (String shapeId : ShapeTrackRegistry.shapeIds()) {
                if (shapeId.equals(selectedId[0])) continue;
                if (ImGui.selectable(shapeId, shapeId.equals(parentId[0]))) {
                    parentId[0] = shapeId;
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
        TextSettings currentText = state.text() == null ? TextSettings.defaults() : state.text();
        ImString textValue = new ImString(currentText.value(), 4096);
        ImBoolean holdText = new ImBoolean(currentText.holdText());
        ImBoolean textShadow = new ImBoolean(currentText.shadow());
        ImBoolean textOutline = new ImBoolean(currentText.outline());
        ImString model = new ImString(state.shapeType().equals("obj")
                && state.model() != null && !state.model().isBlank()
                ? state.model() : "ryansrenderingkit:models/monkey.obj", 512);
        ImString imageFile = new ImString(state.shapeType().equals("image") && state.model() != null
                ? state.model() : "", 1024);
        ImString videoFile = new ImString(state.shapeType().equals("video") && state.model() != null
                ? state.model() : "", 1024);
        ImInt videoStartTick = new ImInt(state.videoStartTick());
        ImBoolean playAudio = new ImBoolean(state.playAudio());
        ImBoolean videoAutoPlay = new ImBoolean(!state.manualPlayback());
        ImBoolean videoLoop = new ImBoolean(!state.noLoop());
        VideoShape liveVideoShape = state.shapeType().equals("video")
                && ShapeTrackRegistry.shape(state.shapeId()) instanceof VideoShape shape ? shape : null;
        float videoDuration = liveVideoShape != null && liveVideoShape.duration() > 0
                ? (float) liveVideoShape.duration() : 3600f;
        float[] playbackSeconds = {(float) state.playbackSeconds()};
        ImString font = new ImString(currentText.fontOrDefault(), 256);
        String[] content = {switch (selectedType[0]) {
            case "block" -> state.shapeType().equals("block") && state.model() != null && !state.model().isBlank()
                    ? state.model() : "minecraft:stone";
            case "item" -> state.shapeType().equals("item") && state.model() != null && !state.model().isBlank()
                    ? state.model() : "minecraft:diamond";
            default -> state.model() == null ? "" : state.model();
        }};
        Map<String, String> blockProperties = new LinkedHashMap<>(state.shapeType().equals("block")
                && state.blockProperties() != null ? state.blockProperties() : Map.of());
        String[] billboard = {currentText.billboard()};

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
            case "line", "arrow" -> {
                while (points.size() < 2) points.add(new ShapePoint(0, 0, 0));
                changed |= editPoint("Start", points, 0);
                changed |= editPoint("End", points, 1);
                changed |= ImGui.dragFloat("Line Width", width, 0.01f, 0.001f, 100.0f);
                if (selectedType[0].equals("arrow"))
                    changed |= ImGui.dragFloat("Head Size", size, 0.01f, 0.01f, 100.0f);
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
            case "text" -> {
                changed |= ImGui.inputTextMultiline("Text", textValue, 420, 100);
                changed |= ImGui.checkbox("Hold Text", holdText);
                changed |= ImGui.checkbox("Shadow", textShadow);
                changed |= ImGui.checkbox("Outline", textOutline);
                changed |= ImGui.inputText("Font Resource", font);
                ImGui.setNextItemWidth(180);
                if (ImGui.beginCombo("Billboard", billboard[0])) {
                    for (String mode : List.of("FIXED", "VERTICAL", "HORIZONTAL", "ALL")) {
                        if (ImGui.selectable(mode, mode.equals(billboard[0]))) {
                            billboard[0] = mode;
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
            }
            case "obj" -> changed |= ImGui.inputText("OBJ Resource", model);
            case "image" -> changed |= ImGui.inputText("Image File", imageFile);
            case "video" -> {
                changed |= ImGui.inputText("Video File", videoFile);
                changed |= ImGui.checkbox("Auto Play", videoAutoPlay);
                if (videoAutoPlay.get()) {
                    changed |= ImGui.inputInt("Start Tick", videoStartTick);
                    changed |= ImGui.checkbox("Loop", videoLoop);
                } else {
                    changed |= ImGui.sliderFloat("Playback Position (s)", playbackSeconds, 0f, videoDuration);
                }
                changed |= ImGui.checkbox("Play Audio", playAudio);
            }
            case "block" -> {
                ImGui.setNextItemWidth(360);
                if (ImGui.beginCombo("Block", content[0])) {
                    for (String id : ShapeTrackRegistry.blockIds()) {
                        if (ImGui.selectable(id, id.equals(content[0]))) {
                            content[0] = id;
                            blockProperties.clear();
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
                BlockState blockState = ShapeTrackRegistry.blockState(content[0], blockProperties);
                for (Property<?> property : blockState.getProperties()) {
                    String name = property.getName();
                    String value = blockProperties.getOrDefault(name, propertyValue(blockState, property));
                    ImGui.setNextItemWidth(220);
                    if (ImGui.beginCombo(name, value)) {
                        for (String option : propertyValues(property)) {
                            if (ImGui.selectable(option, option.equals(value))) {
                                blockProperties.put(name, option);
                                changed = true;
                            }
                        }
                        ImGui.endCombo();
                    }
                }
            }
            case "item" -> {
                ImGui.setNextItemWidth(360);
                if (ImGui.beginCombo("Item", content[0])) {
                    for (String id : ShapeTrackRegistry.itemIds()) {
                        if (ImGui.selectable(id, id.equals(content[0]))) {
                            content[0] = id;
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
            }
        }
        changed |= ImGui.checkbox("See Through", seeThrough);
        changed |= ImGui.checkbox("Visible", visible);

        if (changed) {
            int argb = (Math.round(color[3] * 255) << 24) | (Math.round(color[0] * 255) << 16)
                    | (Math.round(color[1] * 255) << 8) | Math.round(color[2] * 255);
            ShapeState replacement = state.with(position, rotation, scale, size,
                    Math.max(3, segments.get()), Math.max(0.001f, width[0]), argb,
                    points, selectedType[0].equals("text")
                            ? new TextSettings(textValue.get(), holdText.get(), textShadow.get(),
                                    textOutline.get(), billboard[0], font.get())
                            : state.text(),
                    parentId[0],
                    seeThrough.get(), visible.get(), playAudio.get(),
                    selectedType[0].equals("video") ? !videoAutoPlay.get() : state.manualPlayback(),
                    selectedType[0].equals("video") ? !videoLoop.get() : state.noLoop(),
                    selectedType[0].equals("video") ? playbackSeconds[0] : state.playbackSeconds())
                    .withIdentity(selectedType[0], selectedId[0])
                    .withModel(switch (selectedType[0]) {
                        case "obj" -> model.get().isBlank()
                                ? "ryansrenderingkit:models/monkey.obj" : model.get();
                        case "block", "item" -> content[0];
                        case "image" -> imageFile.get();
                        case "video" -> videoFile.get();
                        default -> state.model();
                    })
                    .withBlockProperties(selectedType[0].equals("block")
                            ? blockProperties : state.blockProperties())
                    .withVideoStartTick(selectedType[0].equals("video") ? videoStartTick.get() : state.videoStartTick());
            ShapeTrackRegistry.apply(replacement);
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

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> List<String> propertyValues(Property<T> property) {
        return property.getPossibleValues().stream().map(property::getName).toList();
    }
}
