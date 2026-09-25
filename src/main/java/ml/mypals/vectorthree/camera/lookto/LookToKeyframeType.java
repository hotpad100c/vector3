package ml.mypals.vectorthree.camera.lookto;

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
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.TreeMap;

/** Forces the camera to look at a target between a keyframe and the next, until one ends the scope. */
public final class LookToKeyframeType extends CustomKeyframeType<LookTo> {
    public static final LookToKeyframeType INSTANCE = new LookToKeyframeType();

    private LookToKeyframeType() {
        super("vector3_look_to", "vector3.keyframe_type.look_to", LookTo.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.LOOK_TO_TRACK, ""); }

    @Override
    protected LookTo createValue() {
        return LookTo.of(false, TargetEditor.defaultTarget());
    }

    @Override
    protected LookTo edit(LookTo value) {
        LookTo edited = value;
        if (ImGui.checkbox(I18n.get("vector3.look_to.ends_scope"), edited.endsScope())) {
            edited = edited.withEndsScope(!edited.endsScope());
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.look_to.ends_scope.tooltip"));
        Target target = TargetEditor.edit(edited.target());
        return target.equals(edited.target()) ? edited : edited.withTarget(target);
    }

    // Only reached through createChange, e.g. Flashback applying a single keyframe; scopes use customKeyframeChange.
    @Override
    protected void apply(LookTo value, KeyframeHandler handler) {
        LookToCamera.request(value, value, 0);
    }

    @Override
    protected LookTo sanitize(LookTo value) {
        return LookTo.of(value.endsScope(), value.target().sanitized());
    }

    @Override public boolean hasCustomKeyframeChangeCalculation() { return true; }

    @Override
    public @Nullable KeyframeChange customKeyframeChange(TreeMap<Integer, Keyframe> keyframes, float tick) {
        ScopedKeyframes.Segment<LookTo> segment = ScopedKeyframes.segment(keyframes, tick, LookTo::endsScope);
        return segment == null ? null : action(handler -> LookToCamera.request(segment.from(), segment.to(), segment.amount()));
    }

    @Override
    protected boolean drawOnTimeline(CustomKeyframe<LookTo> keyframe, ImDrawList drawList, int size, float x, float y,
            int colour, float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        return ScopedKeyframes.draw(keyframe.value.endsScope(), drawList, size, x, y, colour, ticksPerPixel, minX, maxX, tick, keyframes);
    }
}
