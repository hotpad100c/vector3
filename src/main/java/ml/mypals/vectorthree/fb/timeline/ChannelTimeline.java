package ml.mypals.vectorthree.fb.timeline;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ml.mypals.vectorthree.fb.channel.ChannelMasks;
import ml.mypals.vectorthree.fb.channel.ChannelRows;
import ml.mypals.vectorthree.fb.channel.ChannelSpec;
import ml.mypals.vectorthree.fb.channel.Channels;
import ml.mypals.vectorthree.fb.multiedit.PropertySelection;
import net.minecraft.client.resources.language.I18n;

public final class ChannelTimeline {
    private ChannelTimeline() {}

    public static void channelModeItem(int trackIndex) {
        if (trackIndex < 0 || trackIndex >= Timeline.scene().keyframeTracks.size()) return;
        KeyframeTrack track = Timeline.scene().keyframeTracks.get(trackIndex);
        if (Channels.spec(track.keyframeType) == null) return;
        boolean on = ((Channels.TrackHolder) track).vector3$perChannel();
        if (ImGui.menuItem(I18n.get("vector3.channels.track"), "", on)) {
            Timeline.upgradeToSceneWrite();
            Channels.setEnabled(track, !on);
            Timeline.keyframesChanged();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.channels.track.tooltip"));
    }

    // Tracks keyed per channel: translucent dark purple (ImGui colours are ABGR).
    static final int CHANNEL_TINT = 0x40602040;

    // Locked tracks: translucent dark red.
    static final int LOCKED_TINT = 0x40101870;
    static int channelTrack = -1, channelTick = -1;
    static Object channelResult;

    public static float channelButtonsWidth() {
        return ImGui.getFrameHeight() * 3 + ImGui.getStyle().getItemSpacingX() * 2;
    }

    // Beside each channel's first row, DaVinci-style: previous keyframe / key or unkey at the playhead / next
    // keyframe, for that channel only. The diamond is filled when the keyframe at the playhead keys it.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void channelButtons(KeyframeTrack track, List<ChannelRows.Row> marked) {
        int row = Timeline.editingTrack(), tick = Timeline.editingTick();
        Keyframe keyframe = track.keyframesByTick.get(tick);
        ChannelSpec spec = Channels.spec(track.keyframeType);
        if (keyframe == null || spec == null) return;
        List<String> channels = spec.channels(track.keyframesByTick.values());

        // Flashback's own keyframes are edited by Flashback: key whatever changed since the last frame.
        Object result = spec.result(keyframe);
        if (row == channelTrack && tick == channelTick && channelResult != null && result != null
                && ChannelMasks.of(keyframe) != null) {
            java.util.Set<String> changed = spec.changed(channelResult, result, channels);
            if (!changed.isEmpty()) {
                java.util.Set<String> mask = new java.util.LinkedHashSet<>(ChannelMasks.of(keyframe));
                mask.addAll(changed);
                ChannelMasks.set(keyframe, mask);
            }
        }
        channelTrack = row;
        channelTick = tick;
        channelResult = result;

        List<ChannelRows.Row> rows = new ArrayList<>(marked);
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (ChannelRows.Row marker : rows) placed.add(marker.channel());
        for (PropertySelection.RowInfo info : PropertySelection.rows()) {
            String label = info.key().substring(0, Math.max(0, info.key().lastIndexOf('#')));
            int hidden = label.indexOf("##");
            if (hidden >= 0) label = label.substring(0, hidden);
            String channel = spec.channelOfLabel(label);
            if (channel != null && channels.contains(channel) && placed.add(channel)) {
                rows.add(new ChannelRows.Row(channel, info.y0(), info.y1()));
            }
        }
        if (rows.isEmpty()) return;

        int cursor = TimelineWindow.getCursorTick();
        Keyframe atCursor = track.keyframesByTick.get(cursor);
        float saveX = ImGui.getCursorScreenPosX(), saveY = ImGui.getCursorScreenPosY();
        float left = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX() - channelButtonsWidth();
        for (ChannelRows.Row marker : rows) {
            String channel = marker.channel();
            ImGui.pushID("vector3Channel" + channel);
            ImGui.setCursorScreenPos(left, (marker.y0() + marker.y1() - ImGui.getFrameHeight()) / 2);
            Integer previous = channelKey(track, channel, cursor, false);
            ImGui.beginDisabled(previous == null);
            if (ImGui.arrowButton("##previous", imgui.moulberry90.flag.ImGuiDir.Left)) jumpToKeyframe(row, previous);
            ImGui.endDisabled();
            ImGui.sameLine();
            boolean keyedHere = atCursor != null && ChannelMasks.keys(atCursor, channel);
            boolean pressed = ImGui.button("##key", ImGui.getFrameHeight(), ImGui.getFrameHeight());
            drawDiamond(keyedHere);
            if (ImGui.isItemHovered()) ImGui.setTooltip(spec.label(channel) + "\n" + I18n.get(atCursor != null
                    ? "vector3.channels.toggle_here.tooltip" : "vector3.channels.key_here.tooltip"));
            if (pressed) {
                if (atCursor != null) {
                    setMask(track, row, cursor, atCursor, ChannelMasks.with(atCursor, channels, channel, !keyedHere), channels);
                } else {
                    keyChannelAt(track, row, cursor, channel, spec, keyframe);
                }
            }
            ImGui.sameLine();
            Integer following = channelKey(track, channel, cursor, true);
            ImGui.beginDisabled(following == null);
            if (ImGui.arrowButton("##next", imgui.moulberry90.flag.ImGuiDir.Right)) jumpToKeyframe(row, following);
            ImGui.endDisabled();
            ImGui.popID();
        }
        ImGui.setCursorScreenPos(saveX, saveY);
        if (ImGui.smallButton(I18n.get("vector3.channels.all")) && ChannelMasks.of(keyframe) != null) {
            setMask(track, row, tick, keyframe, null, channels);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("vector3.channels.all.tooltip"));
    }

    public static void setMask(KeyframeTrack track, int row, int tick, Keyframe keyframe,
            @org.jetbrains.annotations.Nullable java.util.Set<String> mask, List<String> channels) {
        if (mask != null && mask.containsAll(channels)) mask = null;
        Keyframe replacement = keyframe.copy();
        ChannelMasks.set(replacement, mask);
        Timeline.upgradeToSceneWrite();
        Timeline.scene().push(new EditorSceneHistoryEntry(
                List.of(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, tick, keyframe.copy())),
                List.of(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, tick, replacement)),
                I18n.get("vector3.history.channels")));
        Timeline.keyframesChanged();
    }

    // A diamond on the last item: filled when keyed, outlined when not.
    public static void drawDiamond(boolean filled) {
        float cx = (ImGui.getItemRectMinX() + ImGui.getItemRectMaxX()) / 2, cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) / 2;
        float r = (ImGui.getItemRectMaxY() - ImGui.getItemRectMinY()) * 0.3f;
        int colour = ImGui.getColorU32(imgui.moulberry90.flag.ImGuiCol.Text);
        ImDrawList drawList = ImGui.getWindowDrawList();
        if (filled) drawList.addQuadFilled(cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy, colour);
        else drawList.addQuad(cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy, colour, 1.5f);
    }

    /** The nearest keyframe before (or after) {@code tick} that keys {@code channel}. */
    public static Integer channelKey(KeyframeTrack track, String channel, int tick, boolean after) {
        Map.Entry<Integer, Keyframe> entry = after ? track.keyframesByTick.higherEntry(tick) : track.keyframesByTick.lowerEntry(tick);
        while (entry != null && !ChannelMasks.keys(entry.getValue(), channel)) {
            entry = after ? track.keyframesByTick.higherEntry(entry.getKey()) : track.keyframesByTick.lowerEntry(entry.getKey());
        }
        return entry == null ? null : entry.getKey();
    }

    public static void jumpToKeyframe(int row, int tick) {
        ReplayServer server = com.moulberry.flashback.Flashback.getReplayServer();
        if (server != null) server.goToReplayTick(tick);
        selectChannelKeyframe(row, tick);
    }

    public static void selectChannelKeyframe(int row, int tick) {
        KeyframeTrack track = Timeline.scene().keyframeTracks.get(row);
        IntSet ticks = new IntOpenHashSet();
        ticks.add(tick);
        Timeline.selected().clear();
        Timeline.selected().add(new SelectedKeyframes(track.keyframeType, row, ticks));
        Timeline.setEditingTrack(row);
        Timeline.setEditingTick(tick);
    }

    // A new keyframe at the playhead keying only this channel, holding what the track shows there now.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void keyChannelAt(KeyframeTrack track, int row, int tick, String channel, ChannelSpec spec,
            Keyframe template) {
        KeyframeChange change = track.createKeyframeChange(tick, null);
        if (change == null) {
            Map.Entry<Integer, Keyframe> nearest = track.keyframesByTick.floorEntry(tick);
            if (nearest == null) nearest = track.keyframesByTick.firstEntry();
            change = nearest.getValue().createChange();
        }
        Object value = spec.result(change);
        Keyframe created = value == null ? null : spec.keyframe(value, template);
        if (created == null) {
            ReplayUI.setInfoOverlayShort(I18n.get("vector3.channels.cannot_key"));
            return;
        }
        ChannelMasks.set(created, java.util.Set.of(channel));
        Timeline.upgradeToSceneWrite();
        Timeline.scene().push(new EditorSceneHistoryEntry(
                List.of(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, row, tick)),
                List.of(new EditorSceneHistoryAction.SetKeyframe(track.keyframeType, row, tick, created)),
                I18n.get("vector3.history.key_channel", spec.label(channel))));
        selectChannelKeyframe(row, tick);
        Timeline.keyframesChanged();
    }

    // Keyframes keying only some channels have the lower half of their diamond darkened; hovering lists the channels.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void drawPartialKeyframes(float x, float y) {
        if (Timeline.scene() == null) return;
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        float size = Timeline.keyframeSize();
        ImDrawList drawList = ImGui.getWindowDrawList();
        for (int row = 0; row < Timeline.scene().keyframeTracks.size(); row++) {
            KeyframeTrack track = Timeline.scene().keyframeTracks.get(row);
            if (!Channels.enabled(track)) continue;
            ChannelSpec spec = Channels.spec(track.keyframeType);
            float middle = y + 2 + row * lineHeight + lineHeight / 2;
            for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                java.util.Set<String> mask = ChannelMasks.of(entry.getValue());
                if (mask == null) continue;
                float keyX = x + Timeline.xOf(entry.getKey());
                if (keyX < x - size || keyX > x + Timeline.width() + size) continue;
                drawList.addTriangleFilled(keyX - size, middle, keyX + size, middle, keyX, middle + size, 0xC0101010);
                if (Math.abs(Timeline.mouseX() - keyX) <= size && Math.abs(Timeline.mouseY() - middle) <= size) {
                    StringBuilder text = new StringBuilder(I18n.get("vector3.channels.keyed")).append(':');
                    for (String channel : mask) text.append("\n  ").append(spec.label(channel));
                    ImGui.setTooltip(text.toString());
                }
            }
        }
    }
}
