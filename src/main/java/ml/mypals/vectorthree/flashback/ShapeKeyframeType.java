package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.shape.ShapeState;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.UUID;

public final class ShapeKeyframeType implements KeyframeType<ShapeKeyframe> {
    public static final String ID = "vector3_shapes";
    public static final ShapeKeyframeType INSTANCE = new ShapeKeyframeType();

    private ShapeKeyframeType() {}

    public static void register() { KeyframeRegistry.register(INSTANCE); }
    @Override public Class<? extends KeyframeChange> keyframeChangeType() { return ShapeKeyframeChange.class; }
    @Override public boolean supportsHandler(KeyframeHandler handler) { return handler instanceof MinecraftKeyframeHandler; }
    @Override public boolean allowApplyingDuplicateKeyframeChanges() { return true; }
    @Override public String icon() { return "▣"; }
    @Override public String name() { return I18n.get("vector3.keyframe_type.name"); }
    @Override public String id() { return ID; }

    @Override
    public ShapeKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<ShapeKeyframe> createPopup() {
        ShapeTrackRegistry.Definition[] definitions = java.util.stream.StreamSupport
                .stream(ShapeTrackRegistry.definitions().spliterator(), false)
                .toArray(ShapeTrackRegistry.Definition[]::new);
        String[] selected = {definitions[0].id()};
        return () -> {
            ShapeTrackRegistry.Definition current = ShapeTrackRegistry.definition(selected[0]);
            ImGui.setNextItemWidth(240);
            if (ImGui.beginCombo(I18n.get("vector3.keyframe.shape"), I18n.get(current.name()))) {
                for (ShapeTrackRegistry.Definition definition : definitions) {
                    if (ImGui.selectable(I18n.get(definition.name()), definition.id().equals(selected[0]))) {
                        selected[0] = definition.id();
                    }
                }
                ImGui.endCombo();
            }
            if (ImGui.button(I18n.get("vector3.keyframe_type.add")) || ReplayUI.consumeConfirm()) return create(selected[0]);
            ImGui.sameLine();
            if (ImGui.button(I18n.get("vector3.keyframe_type.cancel")) || ReplayUI.consumeCancel()) ImGui.closeCurrentPopup();
            return null;
        };
    }

    private static ShapeKeyframe create(String shapeType) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 cameraPosition = camera.position();
        Vector3fc forward = camera.forwardVector();
        double x = cameraPosition.x + forward.x();
        double y = cameraPosition.y + forward.y();
        double z = cameraPosition.z + forward.z();
        if (shapeType.equals("area")) {
            // AreaShape's default corners are local (-0.5,-0.5,-0.5)/(0.5,0.5,0.5); snapping the
            // center to a block's midpoint makes those corners land on whole-block coordinates.
            x = Math.floor(x) + 0.5;
            y = Math.floor(y) + 0.5;
            z = Math.floor(z) + 0.5;
        }
        ShapeState state = ShapeState.create(shapeType, "vector3:timeline/" + UUID.randomUUID(), x, y, z)
                .withVideoStartTick(TimelineWindow.getCursorTick());
        ShapeTrackRegistry.apply(state);
        return new ShapeKeyframe(state);
    }
}
