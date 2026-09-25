package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.text.FontOptions;
import ml.mypals.vectorthree.shape.point.ShapePoint;
import ml.mypals.vectorthree.shape.ShapeTrackEditor;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.text.SdfFont;
import ml.mypals.vectorthree.shape.text.TextSettings;
import ml.mypals.vectorthree.shape.media.VideoShape;
import ml.mypals.vectorthree.shape.WireframeSettings;
import ml.mypals.vectorthree.shape.area.AreaOptions;
import ml.mypals.vectorthree.shape.area.AreaShape;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/** A Shape track keyframe; {@link #value} is the shape's state at this tick. */
public final class ShapeKeyframe extends CustomKeyframe<ShapeState> {
    private static @Nullable ShapeTrackEditor editor;

    public ShapeKeyframe(ShapeState state) {
        super(ShapeKeyframeType.INSTANCE, state);
    }

    public ShapeKeyframe(ShapeState state, InterpolationType interpolation) {
        super(ShapeKeyframeType.INSTANCE, state, interpolation);
    }

    public static void setEditor(@Nullable ShapeTrackEditor editor) {
        ShapeKeyframe.editor = editor;
    }

    @Override
    public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
        super.renderEditKeyframe(update);
        ShapeTrackEditor current = editor;
        if (current != null) {
            current.edit(this, typed -> update.accept(keyframe -> typed.accept((ShapeKeyframe) keyframe)));
        }
    }

    /** Draws the shape editor and returns the edited state, already applied to the shape. */
    static ShapeState edit(ShapeState state) {
        String[] selectedType = {state.shapeType()};
        String[] selectedId = {state.shapeId()};
        ShapeTrackRegistry.Definition selectedDefinition = ShapeTrackRegistry.definition(selectedType[0]);
        boolean changed = false;
        ImGui.setNextItemWidth(240);
        if (selectedDefinition != null && ImGui.beginCombo(I18n.get("vector3.keyframe.shape"), VectorIcons.withShapeIcon(selectedDefinition.id(), I18n.get(selectedDefinition.name())))) {
            for (ShapeTrackRegistry.Definition definition : ShapeTrackRegistry.definitions()) {
                if (ImGui.selectable(VectorIcons.withShapeIcon(definition.id(), I18n.get(definition.name())), definition.id().equals(selectedType[0]))) {
                    selectedType[0] = definition.id();
                    changed = true;
                }
            }
            ImGui.endCombo();
        }

        ShapeTrackRegistry.previewHighlight(null);
        ImGui.setNextItemWidth(360);
        if (ImGui.beginCombo(I18n.get("vector3.keyframe.shape_uuid"), ShapeTrackRegistry.displayName(selectedId[0]))) {
            for (String shapeId : ShapeTrackRegistry.shapeIds()) {
                if (ImGui.selectable(ShapeTrackRegistry.displayName(shapeId), shapeId.equals(selectedId[0]))) {
                    selectedId[0] = shapeId;
                    String existingType = ShapeTrackRegistry.typeOf(shapeId);
                    if (existingType != null) selectedType[0] = existingType;
                    changed = true;
                }
                if (ImGui.isItemHovered()) ShapeTrackRegistry.previewHighlight(shapeId);
            }
            ImGui.endCombo();
        }

        ImString nameField = new ImString(state.name() == null ? "" : state.name(), 256);
        changed |= ImGui.inputText(I18n.get("vector3.keyframe.name"), nameField);

        float[] position = {(float) state.x(), (float) state.y(), (float) state.z()};
        float[] rotation = {state.pitch(), state.yaw(), state.roll()};
        float[] scale = {(float) state.scaleX(), (float) state.scaleY(), (float) state.scaleZ()};

        String[] parentId = {state.parentShapeId() == null ? "" : state.parentShapeId()};
        ImGui.setNextItemWidth(360);
        if (ImGui.beginCombo(I18n.get("vector3.keyframe.parent_shape_uuid"),
                parentId[0].isEmpty() ? I18n.get("vector3.keyframe.none") : ShapeTrackRegistry.displayName(parentId[0]))) {
            if (ImGui.selectable(I18n.get("vector3.keyframe.none"), parentId[0].isEmpty())) {
                ShapeTrackRegistry.convertToNewParent(state, "", position, rotation, scale);
                parentId[0] = "";
                changed = true;
            }
            for (String shapeId : ShapeTrackRegistry.shapeIds()) {
                if (shapeId.equals(selectedId[0])) continue;
                if (ImGui.selectable(ShapeTrackRegistry.displayName(shapeId), shapeId.equals(parentId[0]))) {
                    ShapeTrackRegistry.convertToNewParent(state, shapeId, position, rotation, scale);
                    parentId[0] = shapeId;
                    changed = true;
                }
                if (ImGui.isItemHovered()) ShapeTrackRegistry.previewHighlight(shapeId);
            }
            ImGui.endCombo();
        }

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
        ImBoolean outline = new ImBoolean(state.outline());
        float[] outlineColor = {
                ((state.outlineColor() >>> 16) & 255) / 255.0f,
                ((state.outlineColor() >>> 8) & 255) / 255.0f,
                (state.outlineColor() & 255) / 255.0f,
                ((state.outlineColor() >>> 24) & 255) / 255.0f
        };
        ImBoolean bypassShaders = new ImBoolean(state.bypassShaders());
        AreaOptions currentArea = AreaOptions.orDefault(state.areaOptions());
        ImBoolean projectEntities = new ImBoolean(currentArea.projectEntities());
        ImBoolean projectParticles = new ImBoolean(currentArea.projectParticles());
        WireframeSettings currentWireframe = WireframeSettings.orDefault(state.wireframe());
        ImBoolean showFaces = new ImBoolean(!currentWireframe.hideFaces());
        ImBoolean wireframeEnabled = new ImBoolean(currentWireframe.enabled());
        ImBoolean separateWireframeColor = new ImBoolean(currentWireframe.separateColor());
        float[] wireframeColor = {
                ((currentWireframe.color() >>> 16) & 255) / 255.0f,
                ((currentWireframe.color() >>> 8) & 255) / 255.0f,
                (currentWireframe.color() & 255) / 255.0f,
                ((currentWireframe.color() >>> 24) & 255) / 255.0f
        };
        TextSettings currentText = state.text() == null ? TextSettings.defaults() : state.text();
        ImString textValue = new ImString(currentText.value(), 4096);
        ImBoolean holdText = new ImBoolean(currentText.holdText());
        ImBoolean textShadow = new ImBoolean(currentText.shadow());
        ImBoolean textOutline = new ImBoolean(currentText.outline());
        ImBoolean textGlow = new ImBoolean(currentText.glow());
        ImBoolean textOutlineGlow = new ImBoolean(currentText.outlineGlow());
        float[] glowStrength = {currentText.glowStrengthOrDefault()};
        ImBoolean glowFollowsText = new ImBoolean(currentText.glowColor() == null);
        int glowArgb = currentText.glowColor() == null ? state.color() : currentText.glowColor();
        float[] glowColor = {((glowArgb >>> 16) & 255) / 255.0f, ((glowArgb >>> 8) & 255) / 255.0f,
                (glowArgb & 255) / 255.0f, ((glowArgb >>> 24) & 255) / 255.0f};
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
            case "entity" -> state.shapeType().equals("entity") && state.model() != null && !state.model().isBlank()
                    ? state.model() : "minecraft:pig";
            default -> state.model() == null ? "" : state.model();
        }};
        Map<String, String> blockProperties = new LinkedHashMap<>(state.shapeType().equals("block")
                && state.blockProperties() != null ? state.blockProperties() : Map.of());
        String[] billboard = {currentText.billboard()};

        changed |= ImGui.dragFloat3(I18n.get("vector3.keyframe.position"), position, 0.05f);
        changed |= ImGui.dragFloat3(I18n.get("vector3.keyframe.rotation"), rotation, 1.0f);
        changed |= ImGui.dragFloat3(I18n.get("vector3.keyframe.scale"), scale, 0.01f, 0.001f, 1000.0f);
        changed |= ImGui.colorEdit4(I18n.get("vector3.keyframe.color"), color);

        List<ShapePoint> points = new ArrayList<>(state.points() == null ? List.of() : state.points());
        switch (selectedType[0]) {
            case "box" ->
                    changed |= ImGui.dragFloat3(I18n.get("vector3.keyframe.dimensions"), size, 0.05f, 0.001f, 1000.0f);
            case "sphere", "face_circle" -> {
                float[] radius = {(float) state.sizeX() / 2};
                if (ImGui.dragFloat(I18n.get("vector3.keyframe.radius"), radius, 0.05f, 0.001f, 1000.0f)) {
                    size[0] = radius[0] * 2;
                    changed = true;
                }
                changed |= ImGui.inputInt(I18n.get("vector3.keyframe.segments"), segments);
            }
            case "cylinder", "cone" -> {
                float[] radius = {(float) state.sizeX() / 2};
                float[] height = {(float) state.sizeY()};
                if (ImGui.dragFloat(I18n.get("vector3.keyframe.radius"), radius, 0.05f, 0.001f, 1000.0f)) {
                    size[0] = radius[0] * 2;
                    changed = true;
                }
                if (ImGui.dragFloat(I18n.get("vector3.keyframe.height"), height, 0.05f, 0.001f, 1000.0f)) {
                    size[1] = height[0];
                    changed = true;
                }
                changed |= ImGui.inputInt(I18n.get("vector3.keyframe.segments"), segments);
            }
            case "line", "arrow" -> {
                while (points.size() < 2) points.add(new ShapePoint(0, 0, 0));
                changed |= editPoint(I18n.get("vector3.keyframe.start"), points, 0);
                changed |= editPoint(I18n.get("vector3.keyframe.end"), points, 1);
                changed |= ImGui.dragFloat(I18n.get("vector3.keyframe.line_width"), width, 0.01f, 0.001f, 100.0f);
                if (selectedType[0].equals("arrow"))
                    changed |= ImGui.dragFloat(I18n.get("vector3.keyframe.head_size"), size, 0.01f, 0.01f, 100.0f);
            }
            case "line_strip" -> {
                while (points.size() < 2) points.add(new ShapePoint(points.size(), points.size(), points.size()));
                for (int i = 0; i < points.size(); i++) {
                    changed |= editPoint(I18n.get("vector3.keyframe.point", i + 1), points, i);
                    ImGui.sameLine();
                    if (ImGui.button(I18n.get("vector3.keyframe.remove") + "##point" + i) && points.size() > 2) {
                        points.remove(i--);
                        changed = true;
                    }
                }
                if (ImGui.button(I18n.get("vector3.keyframe.add_point"))) {
                    points.add(points.isEmpty() ? new ShapePoint(0, 0, 0) : points.getLast());
                    changed = true;
                }
                changed |= ImGui.dragFloat(I18n.get("vector3.keyframe.line_width"), width, 0.01f, 0.001f, 100.0f);
            }
            case "text" -> {
                changed |= ImGui.inputTextMultiline(I18n.get("vector3.keyframe.text"), textValue, 420, 100);
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.hold_text"), holdText);
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.shadow"), textShadow);
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.outline"), textOutline);
                if (textOutline.get()) {
                    changed |= ImGui.colorEdit4(I18n.get("vector3.keyframe.outline_color") + "##text", outlineColor);
                    changed |= ImGui.checkbox(I18n.get("vector3.keyframe.outline_glow"), textOutlineGlow);
                }
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.glow"), textGlow);
                if (textGlow.get() || textOutline.get() && textOutlineGlow.get()) {
                    changed |= ImGui.sliderFloat(I18n.get("vector3.keyframe.glow_strength"), glowStrength, 0,
                            TextSettings.MAX_GLOW_STRENGTH);
                }
                if (textGlow.get()) {
                    changed |= ImGui.checkbox(I18n.get("vector3.keyframe.glow_follow_text"), glowFollowsText);
                    if (!glowFollowsText.get()) changed |= ImGui.colorEdit4(I18n.get("vector3.keyframe.glow_color"), glowColor);
                }
                if ((textGlow.get() || textOutlineGlow.get()) && SdfFont.get(font.get()) == null) {
                    ImGui.textDisabled(I18n.get("vector3.keyframe.glow_sdf_only"));
                }
                if (ImGui.beginCombo(I18n.get("vector3.keyframe.font_resource"), font.get())) {
                    for (FontOptions.Option option : FontOptions.list()) {
                        String label = option.sdf() ? option.spec() + "  (SDF)" : option.spec();
                        if (ImGui.selectable(label, option.spec().equals(font.get()))) {
                            font.set(option.spec());
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
                changed |= ImGui.inputText(I18n.get("vector3.keyframe.font_custom"), font);
                ImGui.setNextItemWidth(180);
                if (ImGui.beginCombo(I18n.get("vector3.keyframe.billboard"), billboard[0])) {
                    for (String mode : List.of("FIXED", "VERTICAL", "HORIZONTAL", "ALL")) {
                        if (ImGui.selectable(mode, mode.equals(billboard[0]))) {
                            billboard[0] = mode;
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
            }
            case "obj" -> changed |= ImGui.inputText(I18n.get("vector3.keyframe.obj_resource"), model);
            case "image" -> changed |= ImGui.inputText(I18n.get("vector3.keyframe.image_file"), imageFile);
            case "video" -> {
                changed |= ImGui.inputText(I18n.get("vector3.keyframe.video_file"), videoFile);
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.auto_play"), videoAutoPlay);
                if (videoAutoPlay.get()) {
                    changed |= ImGui.inputInt(I18n.get("vector3.keyframe.start_tick"), videoStartTick);
                    changed |= ImGui.checkbox(I18n.get("vector3.keyframe.loop"), videoLoop);
                } else {
                    changed |= ImGui.sliderFloat(I18n.get("vector3.keyframe.playback_position"), playbackSeconds, 0f, videoDuration);
                }
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.play_audio"), playAudio);
            }
            case "area" -> {
                while (points.size() < 2) {
                    points.add(new ShapePoint(Math.round(state.x() - 0.5), Math.round(state.y() - 0.5), Math.round(state.z() - 0.5)));
                }
                changed |= editAreaPoint(I18n.get("vector3.keyframe.corner_1"), points, 0);
                changed |= editAreaPoint(I18n.get("vector3.keyframe.corner_2"), points, 1);
                if (ImGui.button(I18n.get("vector3.keyframe.center"))) {
                    ShapePoint corner1 = points.get(0), corner2 = points.get(1);
                    position[0] = (float) ((corner1.x() + corner2.x()) / 2.0);
                    position[1] = (float) ((corner1.y() + corner2.y()) / 2.0);
                    position[2] = (float) ((corner1.z() + corner2.z()) / 2.0);
                    changed = true;
                }
                if (ShapeTrackRegistry.shape(state.shapeId()) instanceof AreaShape area) {
                    ImGui.text(I18n.get("vector3.keyframe.baked_blocks", area.blockCount()));
                } else {
                    ImGui.text(I18n.get("vector3.keyframe.baked_blocks_pending"));
                }
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.outline"), outline);
                if (outline.get()) changed |= ImGui.colorEdit4(I18n.get("vector3.keyframe.outline_color"), outlineColor);
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.project_entities"), projectEntities);
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.project_particles"), projectParticles);
            }
            case "block" -> {
                ImGui.setNextItemWidth(360);
                if (ImGui.beginCombo(I18n.get("vector3.keyframe.block"), content[0])) {
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
                if (ImGui.beginCombo(I18n.get("vector3.keyframe.item"), content[0])) {
                    for (String id : ShapeTrackRegistry.itemIds()) {
                        if (ImGui.selectable(id, id.equals(content[0]))) {
                            content[0] = id;
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
            }
            case "entity" -> {
                ImGui.setNextItemWidth(360);
                if (ImGui.beginCombo(I18n.get("vector3.keyframe.entity"), content[0])) {
                    for (String id : ShapeTrackRegistry.entityIds()) {
                        if (ImGui.selectable(id, id.equals(content[0]))) {
                            content[0] = id;
                            changed = true;
                        }
                    }
                    ImGui.endCombo();
                }
            }
        }
        boolean wireframeCapable = ShapeTrackRegistry.supportsWireframe(selectedType[0]);
        if (wireframeCapable) {
            changed |= ImGui.checkbox(I18n.get("vector3.keyframe.show_faces"), showFaces);
            changed |= ImGui.checkbox(I18n.get("vector3.keyframe.wireframe"), wireframeEnabled);
            if (wireframeEnabled.get()) {
                changed |= ImGui.dragFloat(I18n.get("vector3.keyframe.line_width"), width, 0.01f, 0.001f, 100.0f);
                changed |= ImGui.checkbox(I18n.get("vector3.keyframe.separate_wireframe_color"), separateWireframeColor);
                if (separateWireframeColor.get())
                    changed |= ImGui.colorEdit4(I18n.get("vector3.keyframe.wireframe_color"), wireframeColor);
            }
        }
        boolean bypassCapable = ShapeTrackRegistry.usesBypassOption(selectedType[0]);
        if (bypassCapable) {
            changed |= ImGui.checkbox(I18n.get("vector3.keyframe.bypass_shaders"), bypassShaders);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.keyframe.bypass_shaders.tooltip"));
        }
        changed |= ImGui.checkbox(I18n.get("vector3.keyframe.see_through"), seeThrough);
        changed |= ImGui.checkbox(I18n.get("vector3.keyframe.visible"), visible);

        if (changed) {
            int argb = (Math.round(color[3] * 255) << 24) | (Math.round(color[0] * 255) << 16)
                    | (Math.round(color[1] * 255) << 8) | Math.round(color[2] * 255);
            int outlineArgb = (Math.round(outlineColor[3] * 255) << 24) | (Math.round(outlineColor[0] * 255) << 16)
                    | (Math.round(outlineColor[1] * 255) << 8) | Math.round(outlineColor[2] * 255);
            ShapeState replacement = state.with(position, rotation, scale, size,
                    Math.max(3, segments.get()), Math.max(0.001f, width[0]), argb,
                    points, selectedType[0].equals("text")
                            ? new TextSettings(textValue.get(), holdText.get(), textShadow.get(),
                                    textOutline.get(), billboard[0], font.get(), textGlow.get(), textOutlineGlow.get(),
                                    glowStrength[0], glowFollowsText.get() ? null
                                            : (Math.round(glowColor[3] * 255) << 24) | (Math.round(glowColor[0] * 255) << 16)
                                            | (Math.round(glowColor[1] * 255) << 8) | Math.round(glowColor[2] * 255))
                            : state.text(),
                    parentId[0],
                    seeThrough.get(), visible.get(), outline.get(), outlineArgb, playAudio.get(),
                    selectedType[0].equals("video") ? !videoAutoPlay.get() : state.manualPlayback(),
                    selectedType[0].equals("video") ? !videoLoop.get() : state.noLoop(),
                    selectedType[0].equals("video") ? playbackSeconds[0] : state.playbackSeconds())
                    .withIdentity(selectedType[0], selectedId[0])
                    .withWireframe(wireframeCapable
                            ? new WireframeSettings(!showFaces.get(), wireframeEnabled.get(), separateWireframeColor.get(),
                                    (Math.round(wireframeColor[3] * 255) << 24) | (Math.round(wireframeColor[0] * 255) << 16)
                                            | (Math.round(wireframeColor[1] * 255) << 8) | Math.round(wireframeColor[2] * 255))
                            : state.wireframe())
                    .withAreaOptions(selectedType[0].equals("area")
                            ? new AreaOptions(projectEntities.get(), projectParticles.get())
                            : state.areaOptions())
                    .withBypassShaders(ShapeTrackRegistry.usesBypassOption(selectedType[0])
                            ? bypassShaders.get() : state.bypassShaders())
                    .withModel(switch (selectedType[0]) {
                        case "obj" -> model.get().isBlank()
                                ? "ryansrenderingkit:models/monkey.obj" : model.get();
                        case "block", "item", "entity" -> content[0];
                        case "image" -> imageFile.get();
                        case "video" -> videoFile.get();
                        default -> state.model();
                    })
                    .withBlockProperties(selectedType[0].equals("block")
                            ? blockProperties : state.blockProperties())
                    .withVideoStartTick(selectedType[0].equals("video") ? videoStartTick.get() : state.videoStartTick())
                    .withName(nameField.get().isBlank() ? null : nameField.get());
            ShapeTrackRegistry.apply(replacement);
            return replacement;
        }
        return state;
    }

    private static boolean editPoint(String label, List<ShapePoint> points, int index) {
        ShapePoint point = points.get(index);
        float[] value = {(float) point.x(), (float) point.y(), (float) point.z()};
        if (!ImGui.dragFloat3(label, value, 0.05f)) return false;
        points.set(index, new ShapePoint(value[0], value[1], value[2]));
        return true;
    }

    /** AreaShape's corners are absolute-world and always whole blocks. */
    private static boolean editAreaPoint(String label, List<ShapePoint> points, int index) {
        ShapePoint point = points.get(index);
        float[] value = {(float) point.x(), (float) point.y(), (float) point.z()};
        if (!ImGui.dragFloat3(label, value, 1.0f)) return false;
        points.set(index, new ShapePoint(Math.round(value[0]), Math.round(value[1]), Math.round(value[2])));
        return true;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> List<String> propertyValues(Property<T> property) {
        return property.getPossibleValues().stream().map(property::getName).toList();
    }
}
