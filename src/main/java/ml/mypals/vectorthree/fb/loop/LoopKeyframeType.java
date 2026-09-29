package ml.mypals.vectorthree.fb.loop;

import ml.mypals.vectorthree.core.loop.Loop;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import ml.mypals.vectorthree.fb.editor.VectorIcons;
import ml.mypals.vectorthree.fb.custom.CustomKeyframe;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeType;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Sends the playhead back from an end keyframe to the start keyframe a set number of times. */
public final class LoopKeyframeType extends CustomKeyframeType<Loop> {
    public static final LoopKeyframeType INSTANCE = new LoopKeyframeType();
    private static final Map<Integer, Integer> played = new java.util.concurrent.ConcurrentHashMap<>();

    private LoopKeyframeType() {
        super("vector3_loop", "vector3.keyframe_type.loop", Loop.class);
    }

    @Override public String icon() { return VectorIcons.icon(VectorIcons.LOOP_TRACK, null); }
    @Override public boolean allowChangingInterpolationType() { return false; }
    @Override public boolean supportsHandler(KeyframeHandler handler) { return false; }

    @Override protected Loop createValue() { return new Loop(1, false); }
    @Override protected void apply(Loop value, KeyframeHandler handler) {}

    @Override
    protected Loop sanitize(Loop value) {
        return value.count() < 1 ? new Loop(1, value.endsScope()) : value;
    }

    @Override
    protected Loop edit(Loop value) {
        if (ImGui.checkbox(I18n.get("vector3.loop.ends_scope"), value.endsScope())) value = new Loop(value.count(), !value.endsScope());
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.loop.ends_scope.tooltip"));
        if (!value.endsScope()) {
            int[] count = {value.count()};
            if (ImGui.dragInt(I18n.get("vector3.loop.count"), count, 0.1f, 1, 999)) value = new Loop(Math.clamp(count[0], 1, 999), false);
            if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.loop.count.tooltip"));
        }
        return value;
    }

    public static List<Loop.Scope> scopes(EditorState editorState) {
        long stamp = editorState.acquireRead();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            for (KeyframeTrack track : scene.keyframeTracks) {
                if (track.enabled && track.keyframeType == INSTANCE) return scopes(track.keyframesByTick);
            }
            return List.of();
        } finally {
            editorState.release(stamp);
        }
    }

    private static List<Loop.Scope> scopes(TreeMap<Integer, Keyframe> keyframes) {
        List<Loop.Scope> scopes = new ArrayList<>();
        Map.Entry<Integer, Keyframe> open = null;
        for (Map.Entry<Integer, Keyframe> entry : keyframes.entrySet()) {
            Loop loop = CustomKeyframeType.valueOf(entry.getValue());
            if (!loop.endsScope() && open == null) open = entry;
            else if (loop.endsScope() && open != null) {
                scopes.add(new Loop.Scope(open.getKey(), entry.getKey(), CustomKeyframeType.<Loop>valueOf(open.getValue()).count()));
                open = null;
            }
        }
        return scopes;
    }

    /** Playback: where the playhead goes next, jumping back while a scope has repeats left. */
    public static int resolve(List<Loop.Scope> scopes, int current, int target, boolean paused) {
        if (paused) {
            played.clear();
            return target;
        }
        for (Loop.Scope scope : scopes) {
            if (target < scope.start()) played.remove(scope.start());
            if (current < scope.start() || current >= scope.end() || target < scope.end()) continue;
            int done = played.getOrDefault(scope.start(), 0);
            if (done >= scope.count()) continue;
            played.put(scope.start(), done + 1);
            return Math.min(scope.start() + target - scope.end(), scope.end() - 1);
        }
        return target;
    }

    @Override
    protected boolean drawOnTimeline(CustomKeyframe<Loop> keyframe, ImDrawList drawList, int size, float x, float y,
            int colour, float ticksPerPixel, float minX, float maxX, int tick, TreeMap<Integer, Keyframe> keyframes) {
        if (keyframe.value.endsScope()) {
            drawList.addRect(x - size, y - size, x + size, y + size, colour, 0, 0, 2);
            return true;
        }
        for (Loop.Scope scope : scopes(keyframes)) {
            if (scope.start() != tick) continue;
            float left = Math.max(x, minX);
            float right = Math.min(x + (scope.end() - tick) / ticksPerPixel, maxX);
            if (right > left) {
                drawList.addRectFilled(left, y - size * 0.35f, right, y + size * 0.35f, (colour & 0x00FFFFFF) | 0x66000000);
                String label = "x" + (scope.count() + 1);
                if (right - left > ImGui.calcTextSizeX(label) + size * 2) {
                    drawList.addText(left + size + 2, y - ImGui.getTextLineHeight() / 2, colour, label);
                }
            }
        }
        return false;
    }
}
