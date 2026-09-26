package ml.mypals.vectorthree.flashback.skip;

import ml.mypals.vectorthree.clips.ClipProject;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.flashback.VectorIcons;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import net.minecraft.client.resources.language.I18n;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;


public final class SkipKeyframeType extends CustomKeyframeType<Skip> {
    public static final SkipKeyframeType INSTANCE = new SkipKeyframeType();

    private SkipKeyframeType() {
        super("vector3_skip", "vector3.keyframe_type.skip", Skip.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.SKIP_TRACK, null); }
    @Override public boolean allowChangingInterpolationType() { return false; }
    @Override public boolean supportsHandler(KeyframeHandler handler) { return false; }

    @Override protected Skip createValue() { return new Skip(false); }
    @Override protected void apply(Skip value, KeyframeHandler handler) {}

    @Override
    protected Skip edit(Skip value) {
        if (ImGui.checkbox(I18n.get("vector3.skip.ends_scope"), value.endsScope())) value = new Skip(!value.endsScope());
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.skip.ends_scope.tooltip"));
        return value;
    }

    public static NavigableMap<Integer, Integer> scopes(EditorState editorState) {
        long stamp = editorState.acquireRead();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            // Trimmed clips hide the ends of their chunk spans the same way.
            NavigableMap<Integer, Integer> scopes = ClipProject.hiddenRanges(scene);
            for (KeyframeTrack track : scene.keyframeTracks) {
                if (track.enabled && track.keyframeType == INSTANCE) {
                    scopes.putAll(scopes(track.keyframesByTick));
                    break;
                }
            }
            return scopes;
        } finally {
            editorState.release(stamp);
        }
    }

    private static NavigableMap<Integer, Integer> scopes(TreeMap<Integer, Keyframe> keyframes) {
        NavigableMap<Integer, Integer> scopes = new TreeMap<>();
        Integer open = null;
        for (Map.Entry<Integer, Keyframe> entry : keyframes.entrySet()) {
            boolean ends = CustomKeyframeType.<Skip>valueOf(entry.getValue()).endsScope();
            if (!ends && open == null) open = entry.getKey();
            else if (ends && open != null) {
                scopes.put(open, entry.getKey());
                open = null;
            }
        }
        return scopes;
    }

    public static int resolve(NavigableMap<Integer, Integer> scopes, int tick) {
        Map.Entry<Integer, Integer> scope = scopes.floorEntry(tick);
        return scope != null && tick < scope.getValue() ? scope.getValue() : tick;
    }

    @Override
    protected boolean drawOnTimeline(CustomKeyframe<Skip> keyframe, ImDrawList drawList, int size, float x, float y,
            int colour, float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        if (keyframe.value.endsScope()) {
            drawList.addRect(x - size, y - size, x + size, y + size, colour, 0, 0, 2);
            return true;
        }
        Integer end = scopes(keyframes).get(tick);
        if (end != null) {
            float left = Math.max(x, minX);
            float right = Math.min(x + (end - tick) / ticksPerPixel, maxX);
            if (right > left) drawList.addRectFilled(left, y - size * 0.35f, right, y + size * 0.35f,
                    (colour & 0x00FFFFFF) | 0x66000000);
        }
        return false;
    }
}
