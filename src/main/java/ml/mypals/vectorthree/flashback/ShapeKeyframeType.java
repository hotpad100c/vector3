package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeChange;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframes;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.shape.text.TextSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShapeKeyframeType extends CustomKeyframeType<ShapeState> {
    public static final String ID = "vector3_shapes";
    public static final ShapeKeyframeType INSTANCE = new ShapeKeyframeType();
    /** Re-applies only the shape tracks at the cursor (after undo, delete, ...) without touching the camera. */
    public static final KeyframeHandler REFRESH_HANDLER = new KeyframeHandler() {
        @Override
        public boolean supportsKeyframeChange(Class<? extends KeyframeChange> type) {
            return type == CustomKeyframeChange.class;
        }
    };

    private ShapeKeyframeType() {
        super(ID, "vector3.keyframe_type.name", ShapeState.class);
    }

    public static void register() { CustomKeyframes.register(INSTANCE); }

    // Other handlers (e.g. camera path previews) sample keyframes at other ticks and must not move shapes.
    @Override public boolean supportsHandler(KeyframeHandler handler) {
        return handler instanceof MinecraftKeyframeHandler || handler == REFRESH_HANDLER;
    }
    @Override public String icon() { return VectorIcons.icon(VectorIcons.SHAPE_TRACK, "\ue40a"); }
    @Override protected String valueField() { return "shape"; }

    @Override
    protected CustomKeyframe<ShapeState> newKeyframe(ShapeState value, InterpolationType interpolation) {
        return new ShapeKeyframe(value, interpolation);
    }

    @Override
    protected ShapeState createValue() {
        return newState(ShapeTrackRegistry.definitions().iterator().next().id());
    }

    @Override protected ShapeState edit(ShapeState value) { return ShapeKeyframe.edit(value); }
    @Override protected void apply(ShapeState value, KeyframeHandler handler) { ShapeTrackRegistry.apply(value); }
    @Override protected ShapeState lerp(ShapeState from, ShapeState to, double amount) { return from.interpolate(to, amount); }

    @Override
    protected ShapeState smooth(ShapeState p0, ShapeState p1, ShapeState p2, ShapeState p3,
            float t1, float t2, float t3, float amount) {
        return ShapeState.smooth(p0, p1, p2, p3, t1, t2, t3, amount);
    }

    @Override
    protected ShapeState hermite(Map<Float, ShapeState> values, float tick) {
        return ShapeState.hermite(new java.util.TreeMap<>(values), tick);
    }

    @Override
    protected ShapeState sanitize(ShapeState state) {
        if (state.segments() > 0 && state.lineWidth() > 0 && state.model() != null
                && state.blockProperties() != null
                && !(state.shapeType().equals("text") && (state.text() == null || state.text().font() == null))) {
            return state;
        }
        TextSettings text = state.text();
        if (state.shapeType().equals("text")) {
            if (text == null) text = TextSettings.defaults();
            else if (text.font() == null) text = new TextSettings(text.value(), text.holdText(),
                    text.shadow(), text.outline(), text.billboard(), "minecraft:default");
        }
        return new ShapeState(state.shapeType(), state.shapeId(), state.x(), state.y(), state.z(),
                state.pitch(), state.yaw(), state.roll(), state.scaleX(), state.scaleY(), state.scaleZ(),
                state.sizeX(), state.sizeY(), state.sizeZ(),
                state.segments() <= 0 ? 32 : state.segments(),
                state.lineWidth() <= 0 ? 0.05f : state.lineWidth(),
                state.color() == 0 ? 0xFFFFFFFF : state.color(),
                state.points() == null ? List.of() : state.points(),
                text,
                state.model() != null ? state.model() : switch (state.shapeType()) {
                    case "obj" -> "ryansrenderingkit:models/monkey.obj";
                    case "block" -> "minecraft:stone";
                    case "item" -> "minecraft:diamond";
                    case "entity" -> "minecraft:pig";
                    default -> "";
                },
                state.blockProperties() == null ? Map.of() : state.blockProperties(),
                state.parentShapeId() == null ? "" : state.parentShapeId(),
                state.seeThrough(), state.visible(), state.outline(),
                state.outlineColor() == 0 ? 0xFFFFFFFF : state.outlineColor(),
                state.videoStartTick(), state.playAudio(),
                state.manualPlayback(), state.noLoop(), state.playbackSeconds(), state.name(), state.wireframe(),
                state.areaOptions(), state.bypassShaders(), state.mount() == null ? null : state.mount().sanitized());
    }

    /** Picks the shape type first; the full editor is only shown once the shape exists. */
    @Override
    public KeyframeCreatePopup<CustomKeyframe<ShapeState>> createPopup() {
        ShapeTrackRegistry.Definition[] definitions = java.util.stream.StreamSupport
                .stream(ShapeTrackRegistry.definitions().spliterator(), false)
                .toArray(ShapeTrackRegistry.Definition[]::new);
        String[] selected = {definitions[0].id()};
        return () -> {
            ShapeTrackRegistry.Definition current = ShapeTrackRegistry.definition(selected[0]);
            ImGui.setNextItemWidth(240);
            if (ImGui.beginCombo(I18n.get("vector3.keyframe.shape"), VectorIcons.withShapeIcon(current.id(), I18n.get(current.name())))) {
                for (ShapeTrackRegistry.Definition definition : definitions) {
                    if (ImGui.selectable(VectorIcons.withShapeIcon(definition.id(), I18n.get(definition.name())), definition.id().equals(selected[0]))) {
                        selected[0] = definition.id();
                    }
                }
                ImGui.endCombo();
            }
            if (ImGui.button(I18n.get("vector3.keyframe_type.add")) || ReplayUI.consumeConfirm()) {
                ShapeState state = newState(selected[0]);
                ShapeTrackRegistry.apply(state);
                return new ShapeKeyframe(state);
            }
            ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.keyframe_type.cancel")) || ReplayUI.consumeCancel()) ImGui.closeCurrentPopup();
            return null;
        };
    }

    private static ShapeState newState(String shapeType) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 cameraPosition = camera.position();
        Vector3fc forward = camera.forwardVector();
        double x = cameraPosition.x + forward.x();
        double y = cameraPosition.y + forward.y();
        double z = cameraPosition.z + forward.z();
        if (shapeType.equals("area")) {
            x = Math.floor(x) + 0.5;
            y = Math.floor(y) + 0.5;
            z = Math.floor(z) + 0.5;
        }
        return ShapeState.create(shapeType, "vector3:timeline/" + UUID.randomUUID(), x, y, z)
                .withVideoStartTick(TimelineWindow.getCursorTick());
    }
}
