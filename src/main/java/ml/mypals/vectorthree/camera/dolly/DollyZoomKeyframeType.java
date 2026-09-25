package ml.mypals.vectorthree.camera.dolly;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.camera.target.Target;
import ml.mypals.vectorthree.camera.target.TargetEditor;
import ml.mypals.vectorthree.flashback.VectorIcons;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.flashback.custom.ScopedKeyframes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.TreeMap;

public final class DollyZoomKeyframeType extends CustomKeyframeType<DollyZoom> {
    public static final DollyZoomKeyframeType INSTANCE = new DollyZoomKeyframeType();

    private DollyZoomKeyframeType() {
        super("vector3_dolly_zoom", "vector3.keyframe_type.dolly_zoom", DollyZoom.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.DOLLY_ZOOM_TRACK, ""); }

    @Override
    protected DollyZoom createValue() {
        DollyZoom value = new DollyZoom(false, Target.at(TargetEditor.crosshairTarget()), 8, 4, 0, 0);
        return fromCamera(value);
    }

    @Override
    protected DollyZoom edit(DollyZoom value) {
        DollyZoom edited = value;
        if (ImGui.checkbox(I18n.get("vector3.dolly_zoom.ends_scope"), edited.endsScope())) {
            edited = edited.withEndsScope(!edited.endsScope());
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dolly_zoom.ends_scope.tooltip"));
        Target target = TargetEditor.edit(edited.target());
        if (!target.equals(edited.target())) edited = edited.withTarget(target);

        float[] distance = {edited.distance()};
        float[] frame = {edited.frameHeight()};
        float[] view = {edited.yaw(), edited.pitch()};
        boolean changed = ImGui.dragFloat(I18n.get("vector3.dolly_zoom.distance"), distance, 0.05f, 0.2f, 1000);
        changed |= ImGui.dragFloat(I18n.get("vector3.dolly_zoom.frame_height"), frame, 0.02f, 0.05f, 1000);
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.dolly_zoom.frame_height.tooltip"));
        changed |= ImGui.dragFloat2(I18n.get("vector3.dolly_zoom.view"), view, 0.5f);
        if (changed) edited = edited.withShot(Math.max(0.2f, distance[0]), Math.max(0.05f, frame[0]), view[0], Math.clamp(view[1], -90, 90));
        ImGui.textDisabled(I18n.get("vector3.dolly_zoom.fov", String.format("%.1f", DollyZoom.pose(edited, edited, 0, Vec3.ZERO).fov())));
        if (ImGui.button(I18n.get("vector3.dolly_zoom.from_camera"))) edited = fromCamera(edited);
        return edited;
    }

    private static DollyZoom fromCamera(DollyZoom value) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Vec3 target = value.target().resolve(1);
        if (player == null || target == null) return value;
        float distance = (float) Math.max(0.2, player.getEyePosition().distanceTo(target));
        float fov = minecraft.gameRenderer.mainCamera().getFov();
        return value.withShot(distance, DollyZoom.frameHeight(distance, fov), player.getYRot(), player.getXRot());
    }

    @Override
    protected void apply(DollyZoom value, KeyframeHandler handler) {
        DollyZoomCamera.request(value, value, 0);
    }

    @Override
    protected DollyZoom sanitize(DollyZoom value) {
        Target target = value.target() == null ? Target.at(Vec3.ZERO) : value.target().sanitized();
        return new DollyZoom(value.endsScope(), target, Math.max(0.2f, value.distance()), Math.max(0.05f, value.frameHeight()),
                value.yaw(), value.pitch());
    }

    @Override public boolean hasCustomKeyframeChangeCalculation() { return true; }

    @Override
    public @Nullable KeyframeChange customKeyframeChange(TreeMap<Integer, Keyframe> keyframes, float tick) {
        ScopedKeyframes.Segment<DollyZoom> segment = ScopedKeyframes.segment(keyframes, tick, DollyZoom::endsScope);
        return segment == null ? null : action(handler -> DollyZoomCamera.request(segment.from(), segment.to(), segment.amount()));
    }

    @Override
    protected boolean drawOnTimeline(CustomKeyframe<DollyZoom> keyframe, ImDrawList drawList, int size, float x, float y,
            int colour, float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        return ScopedKeyframes.draw(keyframe.value.endsScope(), drawList, size, x, y, colour, ticksPerPixel, minX, maxX, tick, keyframes);
    }
}
