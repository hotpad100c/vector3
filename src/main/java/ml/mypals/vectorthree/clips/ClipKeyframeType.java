package ml.mypals.vectorthree.clips;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import net.minecraft.client.resources.language.I18n;

import java.nio.file.Path;
import java.util.TreeMap;

/** The Clips track: each keyframe is a clip, drawn as a bar across the part of the timeline it shows. */
public final class ClipKeyframeType extends CustomKeyframeType<ClipRef> {
    public static final ClipKeyframeType INSTANCE = new ClipKeyframeType();

    public static final class ClipKeyframe extends CustomKeyframe<ClipRef> {
        ClipKeyframe(ClipRef value, InterpolationType interpolation) {
            super(INSTANCE, value, interpolation);
        }

        @Override
        public float getCustomWidthInTicks() {
            return value.length();
        }
    }

    private ClipKeyframeType() {
        super("vector3_clips", "vector3.keyframe_type.clips", ClipRef.class);
    }

    @Override public String icon() { return ""; }
    @Override public boolean allowChangingInterpolationType() { return false; }
    @Override public boolean canBeCreatedNormally() { return false; }
    @Override public boolean supportsHandler(KeyframeHandler handler) { return false; }

    @Override protected ClipRef createValue() { return new ClipRef("", "", 0, 0, -1, 0, 0); }
    @Override protected void apply(ClipRef value, KeyframeHandler handler) {}

    @Override
    protected CustomKeyframe<ClipRef> newKeyframe(ClipRef value, InterpolationType interpolation) {
        return new ClipKeyframe(value, interpolation);
    }

    @Override
    protected ClipRef edit(ClipRef value) {
        ImGui.text(value.label());
        ImGui.textDisabled(Path.of(value.source()).getFileName().toString());
        ReplayArchive.Info info = ReplayArchive.read(Path.of(value.source()));
        int total = info == null ? Math.max(value.out(), 1) : info.totalTicks();
        int[] range = {value.in(), value.out()};
        ImGui.setNextItemWidth(220);
        if (ImGui.dragInt2(I18n.get("vector3.clips.range"), range, 1, 0, total)) {
            int in = Math.clamp(range[0], 0, total), out = Math.clamp(range[1], in, total);
            value = value.withRange(in, out);
        }
        ImGui.textDisabled(I18n.get("vector3.clips.range_hint", total));
        return value;
    }

    @Override
    protected boolean drawOnTimeline(CustomKeyframe<ClipRef> keyframe, ImDrawList drawList, int size, float x, float y,
            int colour, float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        ClipRef clip = keyframe.value;
        float right = Math.min(maxX, x + clip.length() / Math.max(ticksPerPixel, 1.0e-6f));
        float left = Math.max(minX, x);
        if (right <= left) return true;
        int hue = java.awt.Color.HSBtoRGB((clip.source().hashCode() & 0xFFFF) / 65535f, 0.45f, 0.75f);
        int fill = 0xC0000000 | (hue & 0xFF) << 16 | (hue & 0xFF00) | (hue >> 16 & 0xFF);
        drawList.addRectFilled(left, y - size, right, y + size, fill, 3);
        drawList.addRect(left, y - size, right, y + size, clip.composed() ? colour : 0xFF40A0FF, 3, 0, 1.5f);
        drawList.pushClipRect(left + 3, y - size, right - 3, y + size, true);
        drawList.addText(left + 4, y - ImGui.getTextLineHeight() / 2, 0xFFFFFFFF, clip.label());
        drawList.popClipRect();
        return true;
    }
}
