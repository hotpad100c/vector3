package ml.mypals.vectorthree.fb.timeline;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiFocusedFlags;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.type.ImString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import ml.mypals.vectorthree.fb.Editors;
import ml.mypals.vectorthree.fb.channel.Channels;
import ml.mypals.vectorthree.fb.clips.ClipKeyframeType;
import ml.mypals.vectorthree.fb.expression.ExpressionBindings;
import ml.mypals.vectorthree.fb.loop.LoopKeyframeType;
import ml.mypals.vectorthree.fb.loop.TrackRepeat;
import ml.mypals.vectorthree.fb.prefab.PrefabBasketWindow;
import ml.mypals.vectorthree.fb.prefab.PrefabGroup;
import ml.mypals.vectorthree.fb.prefab.PrefabGroups;
import ml.mypals.vectorthree.fb.skip.SkipKeyframeType;
import ml.mypals.vectorthree.mixin.flashback.GrabMovementInfoAccessor;
import net.minecraft.client.resources.language.I18n;

public final class TrackTimeline {
    private TrackTimeline() {}

    static final List<PrefabGroups.Handle> groupHandles = new ArrayList<>();
    static String draggedGroup;
    static float groupDragStartX;
    static float groupDragStartY;
    static PrefabGroups.Drag groupDrag;
    static float timelineX;
    static String menuGroup;
    static boolean openGroupMenu;
    static final ImString groupName = new ImString(128);
    static List<SelectedKeyframes> rightClickSelection = List.of();
    static boolean openSelectionMenu;

    public static void handlePrefabs() {
        if (ImGui.getDragDropPayload(PrefabBasketWindow.PAYLOAD) instanceof String id
                && ImGui.isMouseReleased(0) && Timeline.mouseInTimeline()) {
            PrefabBasketWindow.place(id, Timeline.tickAt(Timeline.mouseX() - Timeline.x()));
        }
        String atCursor = PrefabBasketWindow.consumePlaceAtCursor();
        if (atCursor != null) PrefabBasketWindow.place(atCursor, TimelineWindow.getCursorTick());
        if (PrefabBasketWindow.consumeSaveRequest()) Editors.PREFABS.beginSave(Timeline.scene(), Timeline.selected());
        ClipTimeline.handleClips();
        Editors.PREFABS.frame(Timeline.scene(), Timeline.state(), Timeline::upgradeToSceneWrite);
        groupMenu();
        selectionMenu();
    }

    public static void rememberSelection() {
        rightClickSelection = ImGui.isMouseClicked(1) ? new ArrayList<>(Timeline.selected()) : List.of();
    }

    public static void restoreSelection() {
        if (rightClickSelection.isEmpty() || !Timeline.selected().isEmpty()) return;
        Timeline.selected().addAll(rightClickSelection);
        rightClickSelection = List.of();
        if (Timeline.openCreateAtTrack() < 0) openSelectionMenu = true;
    }

    public static void createKeyframePopupItems() {
        if (!Timeline.selected().isEmpty()) createGroupItem(-1);
        distributeItem();
    }

    static final imgui.moulberry90.type.ImInt distributeSpan = new imgui.moulberry90.type.ImInt();

    // Spreads the selected keyframes evenly over a number of ticks from the first one (see Distribute).
    public static void distributeItem() {
        java.util.TreeSet<Integer> ticks = Distribute.ticks(Timeline.scene(), Timeline.selected());
        if (ticks.size() < 2 || !ImGui.beginMenu(I18n.get("vector3.distribute"))) return;
        if (ImGui.isWindowAppearing()) distributeSpan.set(ticks.last() - ticks.first());
        ImGui.setNextItemWidth(ImGui.calcTextSizeX("0000000") + ImGui.getFrameHeight() * 2);
        ImGui.inputInt(I18n.get("vector3.distribute.span"), distributeSpan);
        distributeSpan.set(Math.max(ticks.size() - 1, distributeSpan.get()));
        ImGui.textDisabled(I18n.get("vector3.distribute.hint", ticks.size(), ticks.first(),
                ticks.first() + distributeSpan.get()));
        if (ImGui.button(I18n.get("vector3.distribute.apply")) || ImGui.isKeyPressed(ImGuiKey.Enter, false)) {
            Timeline.upgradeToSceneWrite();
            Distribute.Result result = Distribute.apply(Timeline.scene(), Timeline.selected(), distributeSpan.get());
            if (result.entry() == null) {
                if (result.problem() != null) ReplayUI.setInfoOverlayShort(I18n.get(result.problem()));
            } else {
                Timeline.scene().push(result.entry());
                Timeline.selected().clear();
                Timeline.selected().addAll(result.selection());
                if (Timeline.editingTrack() >= 0) Timeline.setEditingTick(result.moved().getOrDefault(Timeline.editingTick(), Timeline.editingTick()));
                Timeline.keyframesChanged();
            }
            ImGui.closeCurrentPopup();
        }
        ImGui.endMenu();
    }

    public static void selectionMenu() {
        if (openSelectionMenu) {
            ImGui.openPopup("##vector3SelectionPopup");
            openSelectionMenu = false;
        }
        if (!ImGui.beginPopup("##vector3SelectionPopup")) return;
        if (Timeline.selected().isEmpty()) ImGui.closeCurrentPopup();
        else {
            createGroupItem(-1);
            distributeItem();
        }
        ImGui.endPopup();
    }

    // The selected keyframes, or the whole track when nothing is selected.
    public static List<SelectedKeyframes> groupTargets(int trackIndex) {
        if (!Timeline.selected().isEmpty() || trackIndex < 0) return new ArrayList<>(Timeline.selected());
        return PrefabGroups.wholeTrack(Timeline.scene(), trackIndex);
    }

    public static void createGroupItem(int trackIndex) {
        if (!ImGui.menuItem("\ue945 " + I18n.get("vector3.prefab.track.create_group"))) return;
        Timeline.upgradeToSceneWrite();
        PrefabGroups.create(Timeline.scene(), I18n.get("vector3.prefab.group.default_name",
                PrefabGroups.groups(Timeline.scene()).size() + 1), groupTargets(trackIndex));
        Timeline.state().markDirty();
    }

    public static void groupMenu() {
        if (openGroupMenu) {
            ImGui.openPopup("##vector3PrefabGroup");
            openGroupMenu = false;
            PrefabGroups.Span opened = span(menuGroup);
            groupName.set(opened == null ? "" : opened.group().name());
        }
        if (!ImGui.beginPopup("##vector3PrefabGroup")) return;
        PrefabGroups.Span span = span(menuGroup);
        if (span == null) {
            ImGui.closeCurrentPopup();
        } else {
            if (ImGui.inputText(I18n.get("vector3.prefab.name"), groupName) && !groupName.get().isBlank()) {
                Timeline.upgradeToSceneWrite();
                PrefabGroups.rename(Timeline.scene(), span.group(), groupName.get().trim());
                Timeline.state().markDirty();
            }
            if (span.group().placed() && ImGui.menuItem(I18n.get("vector3.prefab.group.edit"))) {
                Editors.PREFABS.beginEdit(span.group());
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.save"))) {
                Editors.PREFABS.beginSave(Timeline.scene(), PrefabGroups.selection(Timeline.scene(), span));
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.dissolve"))) {
                Timeline.upgradeToSceneWrite();
                PrefabGroups.dissolve(Timeline.scene(), span);
                Timeline.state().markDirty();
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.delete"))) {
                Timeline.upgradeToSceneWrite();
                Timeline.scene().push(PrefabGroups.delete(Timeline.scene(), span));
                Timeline.state().markDirty();
            }
        }
        ImGui.endPopup();
    }

    public static PrefabGroups.Span span(String groupId) {
        if (groupId == null || Timeline.scene() == null) return null;
        for (PrefabGroups.Span span : PrefabGroups.spans(Timeline.scene())) {
            if (span.group().id().equals(groupId)) return span;
        }
        return null;
    }

    public static void drawOverlays(float x, float y, float mouseX) {
        groupHandles.clear();
        timelineX = x;
        if (Timeline.scene() == null) return;
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        float barHeight = lineHeight * 0.3f;
        ImDrawList drawList = ImGui.getWindowDrawList();
        dragGroup(x, y, mouseX);
        ClipTimeline.clipEdges(x, y, lineHeight);
        for (int row = 0; row < Timeline.scene().keyframeTracks.size(); row++) {
            float top = y + 2 + row * lineHeight;
            if (Channels.enabled(Timeline.scene().keyframeTracks.get(row))) {
                drawList.addRectFilled(x, top, x + Timeline.width(), top + lineHeight, ChannelTimeline.CHANNEL_TINT);
            }
            if (TrackManagement.locked(Timeline.scene().keyframeTracks.get(row))) {
                drawList.addRectFilled(x, top, x + Timeline.width(), top + lineHeight, ChannelTimeline.LOCKED_TINT);
            }
            if (ExpressionBindings.any(Timeline.scene().keyframeTracks.get(row))) {
                drawList.addText(x + Timeline.width() - ImGui.calcTextSizeX("fx") - 4, top + (lineHeight - ImGui.getTextLineHeight()) / 2,
                        ImGui.getColorU32(imgui.moulberry90.flag.ImGuiCol.TextDisabled), "fx");
            }
        }
        drawRepeats(drawList, x, y, lineHeight);
        // Tracks silenced by another track's Solo are dimmed, so it's visible why they do nothing.
        for (int row = 0; row < Timeline.scene().keyframeTracks.size(); row++) {
            if (ml.mypals.vectorthree.fb.timeline.TrackManagement.audible(Timeline.scene().keyframeTracks.get(row))) continue;
            float top = y + 2 + row * lineHeight;
            drawList.addRectFilled(x, top, x + Timeline.width(), top + lineHeight, 0x70000000);
        }
        drawTrackMoveTargets(drawList, x, y, lineHeight, trackMovePlan(y));
        drawTrackMoveTargets(drawList, x, y, lineHeight, groupMovePlan(y));
        for (PrefabGroups.Span span : PrefabGroups.spans(Timeline.scene())) {
            float left = x + Timeline.xOf(span.firstTick()) - 5;
            float right = Math.max(left + 10, x + Timeline.xOf(span.lastTick()) + 5);
            int colour = groupColour(span.group().id());
            for (int row : span.tracks()) {
                float top = y + 2 + row * lineHeight;
                drawList.addRectFilled(left, top, right, top + lineHeight, (colour & 0x00FFFFFF) | 0x28000000, 3);
            }
            float top = y + 2 + span.tracks().getFirst() * lineHeight;
            drawList.addRectFilled(left, top, right, top + barHeight, (colour & 0x00FFFFFF) | 0xD0000000, 2);
            drawList.addText(ImGui.getFont(), (int) Math.max(8, barHeight), left + 3, top - 1, 0xFF000000, span.group().name());
            groupHandles.add(new PrefabGroups.Handle(span.group().id(), left, top, right, top + barHeight, -1));
            for (int row : span.tracks()) {
                float rowTop = y + 2 + row * lineHeight;
                groupHandles.add(new PrefabGroups.Handle(span.group().id(), left, rowTop, right, rowTop + lineHeight, row));
            }
        }
    }

    public static int rowAt(float screenY, float rowsY) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        return (int) Math.floor((screenY - (rowsY + 2)) / lineHeight);
    }

    public static TrackMove.Plan trackMovePlan(float rowsY) {
        if (!Timeline.grabbedKeyframe() || Timeline.scene() == null) return null;
        return TrackMove.plan(Timeline.scene(), Timeline.selected(), rowAt(Timeline.mouseY(), rowsY) - Timeline.grabbedTrack(), null);
    }

    public static TrackMove.Plan groupMovePlan(float rowsY) {
        if (groupDrag == null || Timeline.scene() == null) return null;
        int rowDelta = rowAt(Timeline.mouseY(), rowsY) - rowAt(groupDragStartY, rowsY);
        return groupDrag.plan(Timeline.scene(), rowDelta);
    }

    public static void drawTrackMoveTargets(ImDrawList drawList, float x, float y, float lineHeight,
                                                     TrackMove.Plan plan) {
        if (plan == null) return;
        int colour = plan.valid() ? 0x00FFC850 : 0x003C3CFF;
        for (int target : new java.util.TreeSet<>(plan.targets().values())) {
            if (target < 0) continue;
            float top = y + 2 + target * lineHeight;
            drawList.addRectFilled(x, top, x + Timeline.width(), top + lineHeight, colour | 0x30000000, 3);
            drawList.addRect(x, top, x + Timeline.width(), top + lineHeight, colour | 0xA0000000, 3);
            if (plan.creates(target)) {
                drawList.addText(x + 4, top + (lineHeight - ImGui.getTextLineHeight()) / 2, colour | 0xC0000000,
                        I18n.get("vector3.timeline.new_track"));
            }
        }
    }

    // Dropping on another row of the same type moves the keyframes there instead of Flashback's in-track move.
    public static void moveAcrossTracks(int totalTicks, float rowsY, GrabMovementInfoAccessor movement) {
        TrackMove.Plan plan = trackMovePlan(rowsY);
        int delta = movement.vector3$delta(), pivot = movement.vector3$scalePivot();
        float factor = movement.vector3$scaleFactor();
        IntUnaryOperator retime = tick -> Math.clamp(
                pivot >= 0 ? pivot + Math.round((tick - pivot) * factor) : tick + delta, 0, totalTicks);
        // Alt drops copies where the drag ends and leaves the originals where they were, as in Resolve.
        if (ImGui.getIO().getKeyAlt() && !ImGui.getIO().getKeyCtrl()) {
            List<SelectedKeyframes> selection = new ArrayList<>(Timeline.selected());
            TrackMove.Plan target = plan != null && plan.valid() ? plan : TrackMove.inPlace(Timeline.scene(), selection);
            List<SelectedKeyframes> copies = new ArrayList<>();
            Timeline.upgradeToSceneWrite();
            EditorSceneHistoryEntry entry = TrackMove.copyEntry(Timeline.scene(), selection, target, retime, null, copies);
            movement.vector3$setDelta(0);
            movement.vector3$setScalePivot(-1);
            if (entry == null) return;
            Timeline.scene().push(entry);
            Timeline.selected().clear();
            Timeline.selected().addAll(copies);
            Timeline.keyframesChanged();
            return;
        }
        if (plan == null || !plan.valid()) return;
        Timeline.upgradeToSceneWrite();
        List<SelectedKeyframes> moved = new ArrayList<>();
        Timeline.scene().push(TrackMove.entry(Timeline.scene(), new ArrayList<>(Timeline.selected()), plan, retime, moved));
        Timeline.selected().clear();
        Timeline.selected().addAll(moved);
        // No offset and no scale pivot: Flashback's own move below then has nothing to do.
        movement.vector3$setDelta(0);
        movement.vector3$setScalePivot(-1);
        Timeline.keyframesChanged();
    }

    public static void dragGroup(float x, float rowsY, float mouseX) {
        if (draggedGroup == null) return;
        if (groupDrag == null) {
            PrefabGroups.Span span = span(draggedGroup);
            if (span == null) {
                draggedGroup = null;
                return;
            }
            groupDrag = new PrefabGroups.Drag(Timeline.scene(), span);
        }
        Timeline.upgradeToSceneWrite();
        if (ImGui.isMouseClicked(1) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            groupDrag.cancel(Timeline.scene());
            groupDrag = null;
            draggedGroup = null;
            Timeline.keyframesChanged();
            return;
        }
        if (ImGui.isMouseDown(0)) {
            int delta = Timeline.tickAt(mouseX - x) - Timeline.tickAt(groupDragStartX - x);
            if (groupDrag.update(Timeline.scene(), delta)) Timeline.keyframesChanged();
            return;
        }
        TrackMove.Plan plan = groupMovePlan(rowsY);
        if (ImGui.getIO().getKeyAlt()) {
            copyGroup(plan, Timeline.tickAt(mouseX - x) - Timeline.tickAt(groupDragStartX - x));
            return;
        }
        var entry = plan != null && plan.valid()
                ? groupDrag.finishAcross(Timeline.scene(), plan)
                : groupDrag.finish(Timeline.scene());
        if (entry != null) Timeline.scene().push(entry);
        groupDrag = null;
        draggedGroup = null;
        Timeline.keyframesChanged();
    }

    // The drag has been showing the group moving; put it back and drop a copy there instead, as a new group.
    public static void copyGroup(TrackMove.Plan plan, int delta) {
        PrefabGroups.Span span = span(draggedGroup);
        groupDrag.cancel(Timeline.scene());
        groupDrag = null;
        draggedGroup = null;
        if (span != null) {
            List<SelectedKeyframes> selection = PrefabGroups.selection(Timeline.scene(), span);
            int shift = Math.max(delta, -span.firstTick());
            TrackMove.Plan target = plan != null && plan.valid() ? plan : TrackMove.inPlace(Timeline.scene(), selection);
            String group = java.util.UUID.randomUUID().toString();
            List<SelectedKeyframes> copies = new ArrayList<>();
            EditorSceneHistoryEntry entry = TrackMove.copyEntry(Timeline.scene(), selection, target, tick -> tick + shift, group, copies);
            if (entry != null) {
                PrefabGroup source = span.group();
                PrefabGroups.groups(Timeline.scene()).put(group, new PrefabGroup(group,
                        I18n.get("vector3.shape.copy_name", source.name()), source.prefab(), source.transform(),
                        source.timeScale(), source.startTick() + shift));
                Timeline.scene().push(entry);
                Timeline.selected().clear();
                Timeline.selected().addAll(copies);
            }
        }
        Timeline.keyframesChanged();
    }

    // Grabbing a selected track's handle drags the whole selection; Alt leaves a copy of the track behind instead,
    // and the original is the one being dragged.
    public static void copyDraggedTrack() {
        if (Timeline.repositioningTrack() < 0 || Timeline.repositioningTrack() >= Timeline.scene().keyframeTracks.size()) return;
        int index = Timeline.repositioningTrack();
        KeyframeTrack original = Timeline.scene().keyframeTracks.get(index);
        Timeline.clearKeyframeSelection();
        if (original.keyframeType == ClipKeyframeType.INSTANCE) {
            Timeline.setRepositioningTrack(-1);
            TrackSelection.only(original);
            return;
        }
        if (!ImGui.getIO().getKeyAlt() && TrackSelection.contains(original) && TrackSelection.size() > 1) {
            Timeline.setRepositioningTrack(-1);
            trackGroupDrag = true;
            trackDragY = Timeline.mouseY();
            return;
        }
        TrackSelection.only(original);
        if (!ImGui.getIO().getKeyAlt()) return;
        if (original.keyframeType == ClipKeyframeType.INSTANCE || original.keyframeType == SkipKeyframeType.INSTANCE) return;
        Timeline.upgradeToSceneWrite();
        Timeline.scene().push(TrackMove.copyTrack(Timeline.scene(), index));
        KeyframeTrack copy = Timeline.scene().keyframeTracks.get(index);
        copy.enabled = original.enabled;
        copy.customName = original.customName;
        copy.customColour = original.customColour;
        Timeline.setRepositioningTrack(index + 1);
        Timeline.selected().clear();
        Timeline.keyframesChanged();
    }

    static float trackPressX;
    static float trackPressY = Float.NaN;
    static boolean trackBoxing;
    static boolean trackGroupDrag;
    static float trackDragY;

    // The track list: click or box-select tracks, drag the selection by a handle, Delete removes it.
    public static void selectTracks(float panelX, float panelY, int middleX) {
        List<KeyframeTrack> tracks = Timeline.scene().keyframeTracks;
        TrackSelection.prune(Timeline.scene());
        if (!Timeline.selected().isEmpty()) TrackSelection.clear();
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        float top = panelY + 6 - (lineHeight - ImGui.getTextLineHeight()) / 2;
        ImDrawList drawList = ImGui.getWindowDrawList();

        if (trackGroupDrag) {
            if (!ImGui.isMouseDown(0)) {
                trackGroupDrag = false;
            } else {
                while (Timeline.mouseY() - trackDragY > lineHeight / 2 && moveTracks(1, lineHeight)) trackDragY += lineHeight;
                while (Timeline.mouseY() - trackDragY < -lineHeight / 2 && moveTracks(-1, lineHeight)) trackDragY -= lineHeight;
            }
        }

        boolean overPanel = ImGui.isWindowHovered() && Timeline.mouseX() >= panelX && Timeline.mouseX() < middleX && Timeline.mouseY() >= panelY;
        if (ImGui.isMouseClicked(0) && overPanel && !ImGui.isAnyItemHovered() && Timeline.repositioningTrack() < 0 && !trackGroupDrag) {
            trackPressX = Timeline.mouseX();
            trackPressY = Timeline.mouseY();
            trackBoxing = false;
        }
        if (!Float.isNaN(trackPressY)) {
            boolean ctrl = ImGui.getIO().getKeyCtrl(), shift = ImGui.getIO().getKeyShift();
            if (ImGui.isMouseDown(0)) {
                if (!trackBoxing && (Math.abs(Timeline.mouseY() - trackPressY) > 4 || Math.abs(Timeline.mouseX() - trackPressX) > 4)) {
                    trackBoxing = true;
                    TrackSelection.beginBox(ctrl || shift);
                }
                if (trackBoxing) {
                    float minY = Math.min(Timeline.mouseY(), trackPressY), maxY = Math.max(Timeline.mouseY(), trackPressY);
                    float minX = Math.min(Timeline.mouseX(), trackPressX), maxX = Math.max(Timeline.mouseX(), trackPressX);
                    drawList.addRectFilled(minX, minY, maxX, maxY, 0x30E0A040);
                    drawList.addRect(minX, minY, maxX, maxY, 0xA0E0A040);
                    int first = Math.max(0, (int) Math.floor((minY - top) / lineHeight));
                    int last = Math.min(tracks.size() - 1, (int) Math.floor((maxY - top) / lineHeight));
                    TrackSelection.box(first <= last ? tracks.subList(first, last + 1) : List.of());
                    Timeline.clearKeyframeSelection();
                }
            } else {
                if (!trackBoxing) {
                    int row = (int) Math.floor((trackPressY - top) / lineHeight);
                    KeyframeTrack hit = row >= 0 && row < tracks.size() ? tracks.get(row) : null;
                    TrackSelection.click(tracks, hit, ctrl, shift);
                    if (hit != null) Timeline.clearKeyframeSelection();
                }
                trackPressY = Float.NaN;
                trackBoxing = false;
            }
        }

        for (int row = 0; row < tracks.size(); row++) {
            KeyframeTrack track = tracks.get(row);
            float rowTop = top + row * lineHeight + (Timeline.repositioningTrack() == row ? Timeline.mouseY() - Timeline.dragStartMouseY() : track.animatedOffsetInUi);
            if (Channels.enabled(track)) drawList.addRectFilled(panelX, rowTop, middleX, rowTop + lineHeight, ChannelTimeline.CHANNEL_TINT);
            if (TrackManagement.locked(track)) drawList.addRectFilled(panelX, rowTop, middleX, rowTop + lineHeight, ChannelTimeline.LOCKED_TINT);
        }
        for (int row = 0; row < tracks.size(); row++) {
            KeyframeTrack track = tracks.get(row);
            if (!TrackSelection.contains(track)) continue;
            float rowTop = top + row * lineHeight + (Timeline.repositioningTrack() == row ? Timeline.mouseY() - Timeline.dragStartMouseY() : track.animatedOffsetInUi);
            drawList.addRectFilled(panelX, rowTop, middleX, rowTop + lineHeight, 0x38E0A040);
        }

        if (!TrackSelection.isEmpty() && !ImGui.getIO().getWantTextInput() && ImGui.isKeyPressed(ImGuiKey.Delete, false)
                && (Timeline.mouseInTimeline() || ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows))) {
            Timeline.upgradeToSceneWrite();
            EditorSceneHistoryEntry removal = TrackSelection.delete(Timeline.scene());
            if (removal == null) return;
            Timeline.scene().push(removal);
            Timeline.clearKeyframeSelection();
            Timeline.keyframesChanged();
        }
    }

    public static boolean moveTracks(int direction, float lineHeight) {
        Timeline.upgradeToSceneWrite();
        if (!TrackSelection.move(Timeline.scene(), direction, lineHeight)) return false;
        Timeline.clearKeyframeSelection();
        Timeline.state().markDirty();
        return true;
    }

    public static int groupColour(String groupId) {
        int rgb = java.awt.Color.HSBtoRGB((groupId.hashCode() & 0xFFFF) / 65535f, 0.55f, 0.95f);
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    // Empty space in a group's rows counts too, but a click that would hit a keyframe stays Flashback's.
    public static String groupAt(float mouseX, float mouseY) {
        for (PrefabGroups.Handle handle : groupHandles) {
            if (mouseX < handle.left() || mouseX > handle.right() || mouseY < handle.top() || mouseY > handle.bottom()) continue;
            if (handle.row() < 0 || !nearKeyframe(handle.row(), mouseX)) return handle.groupId();
        }
        return null;
    }

    public static boolean nearKeyframe(int row, float mouseX) {
        if (row >= Timeline.scene().keyframeTracks.size()) return false;
        TreeMap<Integer, Keyframe> keyframes = Timeline.scene().keyframeTracks.get(row).keyframesByTick;
        int tick = Timeline.tickAt(mouseX - timelineX);
        Map.Entry<Integer, Keyframe> floor = keyframes.floorEntry(tick);
        if (floor != null && floor.getValue().getCustomWidthInTicks() > 0
                && tick <= floor.getKey() + Math.ceil(floor.getValue().getCustomWidthInTicks())) return true;
        for (Integer key : new Integer[]{floor == null ? null : floor.getKey(), keyframes.ceilingKey(tick)}) {
            if (key != null && Math.abs(timelineX + Timeline.xOf(key) - mouseX) < Timeline.keyframeSize()) return true;
        }
        return false;
    }

    // Inside Flashback's track popup, just above "Clear keyframes".
    public static void trackPopupItems(int trackIndex) {
        List<SelectedKeyframes> targets = groupTargets(trackIndex);
        String current = sharedGroup(targets);
        createGroupItem(trackIndex);
        List<PrefabGroups.Span> spans = PrefabGroups.spans(Timeline.scene());
        if (!spans.isEmpty() && ImGui.beginMenu(I18n.get("vector3.prefab.track.add_to_group"))) {
            for (PrefabGroups.Span span : spans) {
                if (ImGui.menuItem(span.group().name() + "###" + span.group().id(), "", span.group().id().equals(current))) {
                    Timeline.upgradeToSceneWrite();
                    PrefabGroups.tagSelection(Timeline.scene(), targets, span.group().id());
                    Timeline.state().markDirty();
                }
            }
            ImGui.endMenu();
        }
        if (current != null && ImGui.menuItem(I18n.get("vector3.prefab.track.leave_group"))) {
            Timeline.upgradeToSceneWrite();
            PrefabGroups.tagSelection(Timeline.scene(), targets, null);
            Timeline.state().markDirty();
        }
        repeatMenu(trackIndex);
        ChannelTimeline.channelModeItem(trackIndex);
        ImGui.separator();
        if (TrackManagement.menu(Timeline.scene(), Timeline.scene().keyframeTracks.get(trackIndex))) Timeline.keyframesChanged();
    }

    public static void repeatMenu(int trackIndex) {
        if (trackIndex < 0 || trackIndex >= Timeline.scene().keyframeTracks.size()) return;
        KeyframeTrack track = Timeline.scene().keyframeTracks.get(trackIndex);
        if (ClipTimeline.trimmable(track) || track.keyframeType == SkipKeyframeType.INSTANCE
                || track.keyframeType == LoopKeyframeType.INSTANCE) return;
        TrackRepeat current = TrackRepeat.of(track);
        if (!ImGui.beginMenu(I18n.get("vector3.repeat") + "###vector3Repeat")) return;
        for (TrackRepeat repeat : TrackRepeat.values()) {
            if (!ImGui.menuItem(I18n.get(repeat.translationKey()), "", repeat == current) || repeat == current) continue;
            Timeline.upgradeToSceneWrite();
            TrackRepeat.set(track, repeat);
            Timeline.keyframesChanged();
        }
        ImGui.separator();
        ImGui.textDisabled(I18n.get("vector3.repeat.tooltip"));
        ImGui.endMenu();
    }

    // Past a repeating track's last keyframe, faint copies show where its keyframes play again.
    public static void drawRepeats(ImDrawList drawList, float x, float y, float lineHeight) {
        float right = x + Timeline.width();
        for (int row = 0; row < Timeline.scene().keyframeTracks.size(); row++) {
            KeyframeTrack track = Timeline.scene().keyframeTracks.get(row);
            TrackRepeat repeat = TrackRepeat.of(track);
            if (repeat == TrackRepeat.NONE || track.keyframesByTick.size() < 2) continue;
            int first = track.keyframesByTick.firstKey(), span = track.keyframesByTick.lastKey() - first;
            if (span <= 0) continue;
            float top = y + 2 + row * lineHeight, middle = top + lineHeight / 2, radius = Timeline.keyframeSize() * 0.45f;
            int colour = track.enabled ? 0x55FFFFFF : 0x30FFFFFF;
            for (int cycle = 1; cycle < 2000; cycle++) {
                int base = first + cycle * span;
                float baseX = x + Timeline.xOf(base);
                if (baseX > right) break;
                float endX = x + Timeline.xOf(base + span);
                if (endX < x) continue;
                drawList.addLine(baseX, top + 3, baseX, top + lineHeight - 3, colour, 1);
                boolean reversed = repeat == TrackRepeat.PING_PONG && (cycle & 1) == 1;
                for (int tick : track.keyframesByTick.keySet()) {
                    int offset = tick - first;
                    float keyX = x + Timeline.xOf(reversed ? base + span - offset : base + offset);
                    drawList.addCircle(keyX, middle, radius, colour, 8, 1.5f);
                }
            }
        }
    }

    public static String sharedGroup(List<SelectedKeyframes> targets) {
        String shared = null;
        for (SelectedKeyframes selected : targets) {
            if (selected.trackIndex() >= Timeline.scene().keyframeTracks.size()) continue;
            TreeMap<Integer, Keyframe> keyframes = Timeline.scene().keyframeTracks.get(selected.trackIndex()).keyframesByTick;
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = keyframes.get(tick);
                String group = keyframe == null ? null : PrefabGroups.groupOf(keyframe);
                if (group == null || (shared != null && !shared.equals(group))) return null;
                shared = group;
            }
        }
        return shared;
    }

    // Ctrl+Delete: delete the selection and pull everything after it back by the time it took up.
    public static void ripple(Runnable original) {
        if (!ImGui.getIO().getKeyCtrl() || Timeline.scene() == null) {
            original.run();
            return;
        }
        Timeline.upgradeToSceneWrite();
        Ripple.Result ripple = Ripple.delete(Timeline.scene(), Timeline.selected());
        if (ripple.entry() == null) {
            ReplayUI.setInfoOverlayShort(I18n.get(ripple.problem()));
            return;
        }
        Timeline.scene().push(ripple.entry());
        Timeline.clearKeyframeSelection();
        Timeline.keyframesChanged();
    }

    public static void protectLocked() {
        if (Timeline.scene() == null) return;
        Timeline.selected().removeIf(selected -> selected.trackIndex() >= 0
                && selected.trackIndex() < Timeline.scene().keyframeTracks.size()
                && TrackManagement.locked(Timeline.scene().keyframeTracks.get(selected.trackIndex())));
    }

    // The add-track menu's check (the first one is the per-track add button): one Skip and one Loop track at most.
    public static boolean singleSkipTrack(KeyframeType<?> type, java.util.function.Predicate<KeyframeType<?>> original) {
        if ((type == SkipKeyframeType.INSTANCE || type == LoopKeyframeType.INSTANCE) && Timeline.scene() != null) {
            for (KeyframeTrack track : Timeline.scene().keyframeTracks) {
                if (track.keyframeType == type) return false;
            }
        }
        return original.test(type);
    }

    public static boolean filterClick(int button) {
        boolean clicked = Timeline.mouseInTimeline() && ImGui.isMouseClicked(button);
        if (draggedGroup != null || ClipTimeline.trimTrack >= 0) return false;
        // A clip's edge trims it instead of moving it.
        if (clicked && button == 0 && (ClipTimeline.hoverEdge == -1 || ClipTimeline.hoverEdge == 1)) {
            ClipTimeline.beginTrim();
            return false;
        }
        String group = clicked && button <= 1 ? groupAt(Timeline.mouseX(), Timeline.mouseY()) : null;
        if (group == null) return clicked;
        // Clicks on a group never reach Flashback: left drags the group, right opens its menu.
        if (button == 0 && draggedGroup == null) {
            draggedGroup = group;
            groupDragStartX = Timeline.mouseX();
            groupDragStartY = Timeline.mouseY();
        } else if (button == 1) {
            menuGroup = group;
            openGroupMenu = true;
        }
        return false;
    }

    public static boolean filterDrag(int button) {
        if (Editors.GIZMO_EDITOR.isDragging() || Editors.ORBIT_GIZMO.isDragging() || Editors.CAMERA_GIZMO.isDragging() || Editors.PREFABS.isDragging()
                || draggedGroup != null) {
            return false;
        }
        return ImGui.isMouseDragging(button);
    }
}
