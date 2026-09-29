package ml.mypals.vectorthree.fb.timeline;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import ml.mypals.vectorthree.core.curve.SpeedCurve;
import ml.mypals.vectorthree.fb.Editors;
import ml.mypals.vectorthree.fb.channel.ChannelRows;
import ml.mypals.vectorthree.fb.channel.Channels;
import ml.mypals.vectorthree.fb.curve.SpeedCurveEditor;
import ml.mypals.vectorthree.fb.curve.SpeedCurves;
import ml.mypals.vectorthree.fb.editor.PropertiesWindow;
import ml.mypals.vectorthree.fb.expression.ExpressionUi;
import ml.mypals.vectorthree.fb.multiedit.MultiEditSession;
import ml.mypals.vectorthree.fb.multiedit.MultiPropertiesPage;
import ml.mypals.vectorthree.fb.multiedit.MultiSelection;
import ml.mypals.vectorthree.fb.multiedit.PropertyClipboard;
import ml.mypals.vectorthree.fb.multiedit.PropertySelection;
import net.minecraft.client.resources.language.I18n;

public final class PropertiesPanel {
    private PropertiesPanel() {}

    public interface ComboCall {
        boolean call(String label, int[] selected, String[] names);
    }

    // Flashback's keyframe popup becomes the Properties window: begin/end are swapped for the window's, and
    // the popup follows the selection instead of needing a right click.
    public static boolean begin(String id, java.util.function.Predicate<String> original) {
        if (!PropertiesWindow.KEYFRAME_POPUP.equals(id)) return original.test(id);
        // Undo can remove tracks or keyframes that the persistent window still points at.
        if (Timeline.scene() != null) {
            int tracks = Timeline.scene().keyframeTracks.size();
            Timeline.selected().removeIf(selected -> selected.trackIndex() >= tracks);
        }
        if (Timeline.selected().size() == 1 && Timeline.selected().getFirst().keyframeTicks().size() == 1) {
            Timeline.setEditingTrack(Timeline.selected().getFirst().trackIndex());
            Timeline.setEditingTick(Timeline.selected().getFirst().keyframeTicks().iterator().nextInt());
        }
        Keyframe editing = editingKeyframe();
        if (editing == null) {
            Timeline.setEditingTrack(-1);
            Timeline.setEditingTick(-1);
        }
        int selected = MultiSelection.count(Timeline.selected());
        if (selected > 1) return PropertiesWindow.begin(true, Long.MIN_VALUE + 1 + Timeline.selected().hashCode(), false);
        return PropertiesWindow.begin(Timeline.editingTrack() >= 0 && Timeline.editingTick() >= 0,
                ((long) Timeline.editingTrack() << 32) | (Timeline.editingTick() & 0xFFFFFFFFL),
                editing != null && SpeedCurves.of(editing) != null);
    }

    public static MultiPropertiesPage.Host multiHost() {
        return new MultiPropertiesPage.Host(() -> Timeline.scene(), () -> Timeline.upgradeToSceneWrite(),
                () -> Timeline.keyframesChanged(), () -> setCurves(SpeedCurve.preset(SpeedCurve.Preset.EASE_IN_OUT)),
                () -> Editors.GIZMO_EDITOR.controls());
    }

    public static Keyframe editingKeyframe() {
        if (Timeline.scene() == null || Timeline.editingTrack() < 0 || Timeline.editingTrack() >= Timeline.scene().keyframeTracks.size()) {
            return null;
        }
        return Timeline.scene().keyframeTracks.get(Timeline.editingTrack()).keyframesByTick.get(Timeline.editingTick());
    }

    public static void page(int totalTicks, Runnable original) {
        if (PropertiesWindow.isCurveTab()) {
            renderCurvePage();
            return;
        }
        PropertySelection.beginFrame(java.util.Objects.hash(Timeline.selected(), Timeline.editingTrack(), Timeline.editingTick()));
        if (MultiSelection.count(Timeline.selected()) > 1) {
            ExpressionUi.begin(null, () -> {}, () -> {});
            MultiPropertiesPage.render(Timeline.selected(), Timeline.editingTrack(), Timeline.editingTick(), multiHost());
        } else {
            // On a track keyed per channel, the editor is narrowed so every channel's row has room for its buttons.
            KeyframeTrack channelTrack = Timeline.editingTrack() >= 0 && Timeline.editingTrack() < Timeline.scene().keyframeTracks.size()
                    ? Timeline.scene().keyframeTracks.get(Timeline.editingTrack()) : null;
            boolean channelButtons = channelTrack != null && Channels.enabled(channelTrack)
                    && channelTrack.keyframesByTick.containsKey(Timeline.editingTick());
            if (channelButtons) {
                ChannelRows.begin();
                ImGui.pushItemWidth(-(ChannelTimeline.channelButtonsWidth() + ImGui.getFontSize() * 7));
            }
            ExpressionUi.begin(channelTrack != null && channelTrack.keyframesByTick.containsKey(Timeline.editingTick()) ? channelTrack : null,
                    () -> Timeline.upgradeToSceneWrite(), () -> Timeline.keyframesChanged());
            MultiEditSession.display("single", List.of(), key -> true, false, () -> original.run());
            if (channelButtons) {
                ImGui.popItemWidth();
                ChannelTimeline.channelButtons(channelTrack, ChannelRows.end());
            }
            ExpressionUi.end();
        }
        List<PropertyClipboard.Clip> paste = PropertySelection.frame();
        if (paste != null) {
            List<SelectedKeyframes> targets = Timeline.selected();
            if (targets.isEmpty() && Timeline.editingTrack() >= 0 && Timeline.editingTrack() < Timeline.scene().keyframeTracks.size()) {
                targets = List.of(new SelectedKeyframes(Timeline.scene().keyframeTracks.get(Timeline.editingTrack()).keyframeType,
                        Timeline.editingTrack(), IntSet.of(Timeline.editingTick())));
            }
            int applied = MultiPropertiesPage.paste(paste, targets, Timeline.editingTrack(), Timeline.editingTick(), multiHost());
            PropertySelection.pasted(applied, paste.size());
        }
    }

    // "Custom" joins the interpolation types: choosing it gives the selected keyframes a speed curve.
    public static boolean customInterpolation(String label, int[] selected, String[] names, ComboCall original) {
        Keyframe editing = editingKeyframe();
        boolean custom = editing != null && SpeedCurves.of(editing) != null;
        String[] withCustom = java.util.Arrays.copyOf(names, names.length + 1);
        withCustom[names.length] = I18n.get("vector3.curve.custom");
        if (custom) selected[0] = names.length;
        if (!original.call(label, selected, withCustom)) return false;
        if (selected[0] == names.length) {
            if (!custom) setCurves(SpeedCurve.preset(SpeedCurve.Preset.EASE_IN_OUT));
            return false;
        }
        clearCurvesOnPush = true;
        return true;
    }

    static boolean clearCurvesOnPush;

    // The keyframes Flashback writes for the new interpolation type are copies, still carrying the curve.
    public static void beforePush(EditorSceneHistoryEntry entry) {
        if (clearCurvesOnPush) {
            for (EditorSceneHistoryAction action : entry.redo()) {
                if (action instanceof EditorSceneHistoryAction.SetKeyframe set) SpeedCurves.set(set.keyframe(), null);
            }
            clearCurvesOnPush = false;
        }
    }

    public static void setCurves(SpeedCurve curve) {
        Timeline.upgradeToSceneWrite();
        List<SelectedKeyframes> targets = Timeline.selected().isEmpty() && Timeline.editingTrack() >= 0
                ? List.of(new SelectedKeyframes(Timeline.scene().keyframeTracks.get(Timeline.editingTrack()).keyframeType,
                        Timeline.editingTrack(), IntSet.of(Timeline.editingTick())))
                : Timeline.selected();
        List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
        for (SelectedKeyframes selected : targets) {
            if (selected.trackIndex() >= Timeline.scene().keyframeTracks.size()) continue;
            KeyframeTrack track = Timeline.scene().keyframeTracks.get(selected.trackIndex());
            if (!track.keyframeType.allowChangingInterpolationType()) continue;
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = track.keyframesByTick.get(tick);
                if (keyframe == null) continue;
                Keyframe curved = keyframe.copy();
                curved.interpolationType(InterpolationType.LINEAR);
                SpeedCurves.set(curved, curve);
                undo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selected.trackIndex(), tick, keyframe.copy()));
                redo.add(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, selected.trackIndex(), tick, curved));
            }
        }
        if (redo.isEmpty()) return;
        Timeline.scene().push(new EditorSceneHistoryEntry(undo, redo, I18n.get("vector3.history.speed_curve")));
        Timeline.keyframesChanged();
    }

    public static void renderCurvePage() {
        Keyframe keyframe = editingKeyframe();
        SpeedCurve curve = keyframe == null ? null : SpeedCurves.of(keyframe);
        if (curve == null) return;
        KeyframeTrack track = Timeline.scene().keyframeTracks.get(Timeline.editingTrack());
        Integer next = track.keyframesByTick.higherKey(Timeline.editingTick());
        float playhead = next == null ? -1
                : (TimelineWindow.getCursorTick() - Timeline.editingTick()) / (float) (next - Timeline.editingTick());
        SpeedCurveEditor.Result result = SpeedCurveEditor.render(curve, playhead);
        if (result == null) return;
        Timeline.upgradeToSceneWrite();
        keyframe = editingKeyframe();
        if (keyframe == null) return;
        SpeedCurves.set(keyframe, result.curve());
        if (result.commit() && !result.curve().equals(result.before())) {
            Keyframe before = keyframe.copy();
            SpeedCurves.set(before, result.before());
            Timeline.scene().push(new EditorSceneHistoryEntry(
                    List.of(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, Timeline.editingTrack(), Timeline.editingTick(), before)),
                    List.of(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, Timeline.editingTrack(), Timeline.editingTick(), keyframe.copy())),
                    I18n.get("vector3.history.speed_curve")));
        }
        Timeline.keyframesChanged();
    }
}
