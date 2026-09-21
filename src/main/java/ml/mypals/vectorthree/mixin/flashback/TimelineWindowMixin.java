package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.shape.ShapeTimelineSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(value = TimelineWindow.class, remap = false)
public abstract class TimelineWindowMixin {
    @Shadow private static EditorScene editorScene;
    @Shadow @Final private static List<SelectedKeyframes> selectedKeyframesList;
    @Shadow private static int editingKeyframeTrack;
    @Shadow private static int editingKeyframeTick;

    @Inject(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;beginPopup(Ljava/lang/String;)Z",
            shift = At.Shift.BEFORE))
    private static void vector3$selectClickedShape(CallbackInfo ci) {
        String shapeId = ShapeTimelineSelection.consume();
        if (shapeId == null) return;

        int cursor = TimelineWindow.getCursorTick();
        int bestTrack = -1;
        int bestTick = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int trackIndex = 0; trackIndex < editorScene.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack track = editorScene.keyframeTracks.get(trackIndex);
            if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                if (!(entry.getValue() instanceof ShapeKeyframe shape)
                        || !shape.state.shapeId().equals(shapeId)) continue;
                int distance = Math.abs(entry.getKey() - cursor);
                if (distance < bestDistance) {
                    bestTrack = trackIndex;
                    bestTick = entry.getKey();
                    bestDistance = distance;
                }
            }
        }
        if (bestTrack < 0) return;

        selectedKeyframesList.clear();
        IntSet ticks = IntSets.singleton(bestTick);
        selectedKeyframesList.add(new SelectedKeyframes(ShapeKeyframeType.INSTANCE, bestTrack, ticks));
        editingKeyframeTrack = bestTrack;
        editingKeyframeTick = bestTick;
        ImGui.openPopup("##KeyframePopup");
    }
}
