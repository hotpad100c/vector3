package ml.mypals.vectorthree.mixin.flashback;

import com.moulberry.flashback.editor.SelectedKeyframes;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import ml.mypals.vectorthree.flashback.skip.SkipKeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import imgui.moulberry90.ImGui;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.flashback.ShapeManagerWindow;
import ml.mypals.vectorthree.flashback.TrackMove;
import ml.mypals.vectorthree.prefab.PrefabBasketWindow;
import ml.mypals.vectorthree.prefab.PrefabGroups;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.flag.ImGuiKey;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.ext.MinecraftExt;
import net.minecraft.client.Minecraft;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.resources.language.I18n;
import ml.mypals.vectorthree.shape.ShapeTimelineSelection;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

@Mixin(value = TimelineWindow.class, remap = false)
public abstract class TimelineWindowMixin {
    @Shadow private static EditorScene editorScene;
    @Shadow private static EditorState editorState;
    @Shadow @Final private static List<SelectedKeyframes> selectedKeyframesList;
    @Shadow private static int editingKeyframeTrack;
    @Shadow private static int editingKeyframeTick;
    @Shadow private static float x;
    @Shadow private static float y;
    @Shadow private static float width;
    @Shadow private static float height;
    @Shadow private static float mouseX;
    @Shadow private static float mouseY;
    @Shadow private static int keyframeSize;
    @Shadow private static int openCreateKeyframeAtTickTrack;
    @Shadow private static boolean grabbedKeyframe;
    @Shadow private static int grabbedKeyframeTrack;
    private static int vector3$lastEditorModCount = -1;
    private static boolean vector3$refreshKeyframes;
    @Shadow private static void upgradeToSceneWrite() {
        throw new AssertionError();
    }
    @Shadow private static int timelineXToReplayTick(float x) {
        throw new AssertionError();
    }
    @Shadow private static int replayTickToTimelineX(int tick) {
        throw new AssertionError();
    }
    @Unique
    private static final List<PrefabGroups.Handle> vector3$groupHandles = new ArrayList<>();
    @Unique
    private static String vector3$draggedGroup;
    @Unique
    private static float vector3$groupDragStartX;
    @Unique
    private static PrefabGroups.Drag vector3$groupDrag;
    @Unique
    private static float vector3$timelineX;
    @Unique
    private static String vector3$menuGroup;
    @Unique
    private static boolean vector3$openGroupMenu;
    @Unique
    private static final ImString vector3$groupName = new ImString(128);
    @Unique
    private static List<SelectedKeyframes> vector3$rightClickSelection = List.of();
    @Unique
    private static boolean vector3$openSelectionMenu;

    @Inject(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;beginPopup(Ljava/lang/String;)Z",
            shift = At.Shift.BEFORE))
    private static void vector3$selectClickedShape(CallbackInfo ci) {
        // Gizmos draw with the see-through managers even when no shape was ever applied.
        ShapeTrackRegistry.fixSeeThroughPipelines();
        Vector3.ORBIT_GIZMO.frame();
        Vector3.GIZMO_EDITOR.frame();
        vector3$handlePrefabs();
        if (ShapeManagerWindow.isEditorMode()) Vector3.EDITOR_CAMERA.frame();
        String shapeId = ShapeTimelineSelection.consume();
        if (shapeId != null) {
            int cursor = TimelineWindow.getCursorTick();
            int bestTrack = -1;
            int bestTick = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int trackIndex = 0; trackIndex < editorScene.keyframeTracks.size(); trackIndex++) {
                KeyframeTrack track = editorScene.keyframeTracks.get(trackIndex);
                if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
                for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                    if (!(entry.getValue() instanceof ShapeKeyframe shape)
                            || !shape.value.shapeId().equals(shapeId)) continue;
                    int distance = Math.abs(entry.getKey() - cursor);
                    if (distance < bestDistance) {
                        bestTrack = trackIndex;
                        bestTick = entry.getKey();
                        bestDistance = distance;
                    }
                }
            }
            if (bestTrack >= 0) {
                selectedKeyframesList.clear();
                IntSet ticks = new IntOpenHashSet();
                ticks.add(bestTick);
                selectedKeyframesList.add(new SelectedKeyframes(ShapeKeyframeType.INSTANCE, bestTrack, ticks));
                editingKeyframeTrack = bestTrack;
                editingKeyframeTick = bestTick;
                ImGui.openPopup("##KeyframePopup");
            }
        }

        vector3$syncGizmoSelection();
    }

    @Unique
    private static void vector3$handlePrefabs() {
        if (ImGui.getDragDropPayload(PrefabBasketWindow.PAYLOAD) instanceof String id
                && ImGui.isMouseReleased(0) && vector3$isMouseInTimeline()) {
            PrefabBasketWindow.place(id, timelineXToReplayTick(mouseX - x));
        }
        String atCursor = PrefabBasketWindow.consumePlaceAtCursor();
        if (atCursor != null) PrefabBasketWindow.place(atCursor, TimelineWindow.getCursorTick());
        if (PrefabBasketWindow.consumeSaveRequest()) Vector3.PREFABS.beginSave(editorScene, selectedKeyframesList);
        Vector3.PREFABS.frame(editorScene, editorState, TimelineWindowMixin::upgradeToSceneWrite);
        vector3$groupMenu();
        vector3$selectionMenu();
    }

    // Flashback's box select feeds window-absolute X where every other caller passes it relative to the window.
    @ModifyArg(method = "releaseGrabbed", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineXToReplayTick(F)I"))
    private static float vector3$boxSelectStart(float timelineX) {
        return timelineX - x;
    }

    @ModifyArg(method = "releaseGrabbed", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineXToReplayTick(F)I"))
    private static float vector3$boxSelectEnd(float timelineX) {
        return timelineX - x;
    }

    @Inject(method = "handleClick", at = @At("HEAD"))
    private static void vector3$rememberSelection(CallbackInfo ci) {
        vector3$rightClickSelection = ImGui.isMouseClicked(1) ? new ArrayList<>(selectedKeyframesList) : List.of();
    }

    @Inject(method = "handleClick", at = @At("RETURN"))
    private static void vector3$restoreSelection(CallbackInfo ci) {
        if (vector3$rightClickSelection.isEmpty() || !selectedKeyframesList.isEmpty()) return;
        selectedKeyframesList.addAll(vector3$rightClickSelection);
        vector3$rightClickSelection = List.of();
        if (openCreateKeyframeAtTickTrack < 0) vector3$openSelectionMenu = true;
    }

    @Inject(method = "renderKeyframeElements", at = @At(value = "CONSTANT", args = "stringValue=flashback.create_keyframe_at_n"))
    private static void vector3$createKeyframePopupItems(float x, float y, int cursorTicks, int middleX, CallbackInfo ci) {
        if (!selectedKeyframesList.isEmpty()) vector3$createGroupItem(-1);
    }

    private static void vector3$selectionMenu() {
        if (vector3$openSelectionMenu) {
            ImGui.openPopup("##vector3SelectionPopup");
            vector3$openSelectionMenu = false;
        }
        if (!ImGui.beginPopup("##vector3SelectionPopup")) return;
        if (selectedKeyframesList.isEmpty()) ImGui.closeCurrentPopup();
        else vector3$createGroupItem(-1);
        ImGui.endPopup();
    }

    // The selected keyframes, or the whole track when nothing is selected.
    private static List<SelectedKeyframes> vector3$groupTargets(int trackIndex) {
        if (!selectedKeyframesList.isEmpty() || trackIndex < 0) return new ArrayList<>(selectedKeyframesList);
        return PrefabGroups.wholeTrack(editorScene, trackIndex);
    }

    private static void vector3$createGroupItem(int trackIndex) {
        if (!ImGui.menuItem("\ue945 " + I18n.get("vector3.prefab.track.create_group"))) return;
        upgradeToSceneWrite();
        PrefabGroups.create(editorScene, I18n.get("vector3.prefab.group.default_name",
                PrefabGroups.groups(editorScene).size() + 1), vector3$groupTargets(trackIndex));
        editorState.markDirty();
    }

    @Unique
    private static void vector3$groupMenu() {
        if (vector3$openGroupMenu) {
            ImGui.openPopup("##vector3PrefabGroup");
            vector3$openGroupMenu = false;
            PrefabGroups.Span opened = vector3$span(vector3$menuGroup);
            vector3$groupName.set(opened == null ? "" : opened.group().name());
        }
        if (!ImGui.beginPopup("##vector3PrefabGroup")) return;
        PrefabGroups.Span span = vector3$span(vector3$menuGroup);
        if (span == null) {
            ImGui.closeCurrentPopup();
        } else {
            if (ImGui.inputText(I18n.get("vector3.prefab.name"), vector3$groupName) && !vector3$groupName.get().isBlank()) {
                upgradeToSceneWrite();
                PrefabGroups.rename(editorScene, span.group(), vector3$groupName.get().trim());
                editorState.markDirty();
            }
            if (span.group().placed() && ImGui.menuItem(I18n.get("vector3.prefab.group.edit"))) {
                Vector3.PREFABS.beginEdit(span.group());
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.save"))) {
                Vector3.PREFABS.beginSave(editorScene, PrefabGroups.selection(editorScene, span));
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.dissolve"))) {
                upgradeToSceneWrite();
                PrefabGroups.dissolve(editorScene, span);
                editorState.markDirty();
            }
            if (ImGui.menuItem(I18n.get("vector3.prefab.group.delete"))) {
                upgradeToSceneWrite();
                editorScene.push(PrefabGroups.delete(editorScene, span));
                editorState.markDirty();
            }
        }
        ImGui.endPopup();
    }

    @Unique
    private static PrefabGroups.Span vector3$span(String groupId) {
        if (groupId == null || editorScene == null) return null;
        for (PrefabGroups.Span span : PrefabGroups.spans(editorScene)) {
            if (span.group().id().equals(groupId)) return span;
        }
        return null;
    }

    @Inject(method = "renderKeyframes", at = @At("HEAD"))
    private static void vector3$drawPrefabGroups(float x, float y, float mouseX, int minTicks, float availableTicks,
            int totalTicks, CallbackInfo ci) {
        vector3$groupHandles.clear();
        vector3$timelineX = x;
        if (editorScene == null) return;
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        float barHeight = lineHeight * 0.3f;
        ImDrawList drawList = ImGui.getWindowDrawList();
        vector3$dragGroup(x, mouseX);
        vector3$drawTrackMoveTargets(drawList, x, y, lineHeight);
        for (PrefabGroups.Span span : PrefabGroups.spans(editorScene)) {
            float left = x + replayTickToTimelineX(span.firstTick()) - 5;
            float right = Math.max(left + 10, x + replayTickToTimelineX(span.lastTick()) + 5);
            int colour = vector3$groupColour(span.group().id());
            for (int row : span.tracks()) {
                float top = y + 2 + row * lineHeight;
                drawList.addRectFilled(left, top, right, top + lineHeight, (colour & 0x00FFFFFF) | 0x28000000, 3);
            }
            float top = y + 2 + span.tracks().getFirst() * lineHeight;
            drawList.addRectFilled(left, top, right, top + barHeight, (colour & 0x00FFFFFF) | 0xD0000000, 2);
            drawList.addText(ImGui.getFont(), (int) Math.max(8, barHeight), left + 3, top - 1, 0xFF000000, span.group().name());
            vector3$groupHandles.add(new PrefabGroups.Handle(span.group().id(), left, top, right, top + barHeight, -1));
            for (int row : span.tracks()) {
                float rowTop = y + 2 + row * lineHeight;
                vector3$groupHandles.add(new PrefabGroups.Handle(span.group().id(), left, rowTop, right, rowTop + lineHeight, row));
            }
        }
    }

    private static TrackMove.Plan vector3$trackMovePlan(float rowsY) {
        if (!grabbedKeyframe || editorScene == null) return null;
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        int row = (int) Math.floor((mouseY - (rowsY + 2)) / lineHeight);
        return TrackMove.plan(editorScene, selectedKeyframesList, row - grabbedKeyframeTrack);
    }

    private static void vector3$drawTrackMoveTargets(ImDrawList drawList, float x, float y, float lineHeight) {
        TrackMove.Plan plan = vector3$trackMovePlan(y);
        if (plan == null) return;
        for (int target : new java.util.TreeSet<>(plan.targets().values())) {
            float top = y + 2 + target * lineHeight;
            drawList.addRectFilled(x, top, x + width, top + lineHeight, 0x30FFC850, 3);
            drawList.addRect(x, top, x + width, top + lineHeight, 0xA0FFC850, 3);
            if (plan.creates(target)) {
                drawList.addText(x + 4, top + (lineHeight - ImGui.getTextLineHeight()) / 2, 0xC0FFC850,
                        I18n.get("vector3.timeline.new_track"));
            }
        }
    }

    // Dropping on another row of the same type moves the keyframes there instead of Flashback's in-track move.
    @Inject(method = "releaseGrabbed", at = @At("HEAD"))
    private static void vector3$moveAcrossTracks(ReplayServer server, int totalTicks, float rowsY, CallbackInfo ci) {
        TrackMove.Plan plan = vector3$trackMovePlan(rowsY);
        if (plan == null) return;
        IntUnaryOperator retime = vector3$retime(totalTicks);
        upgradeToSceneWrite();
        List<SelectedKeyframes> moved = new ArrayList<>();
        editorScene.push(TrackMove.entry(editorScene, new ArrayList<>(selectedKeyframesList), plan, retime, moved));
        selectedKeyframesList.clear();
        selectedKeyframesList.addAll(moved);
        grabbedKeyframe = false;
        vector3$keyframesChanged();
    }

    // Flashback's own tick mapping for the drag (offset, or Alt-scale around a pivot).
    private static IntUnaryOperator vector3$retime(int totalTicks) {
        try {
            Method calculate = TimelineWindow.class.getDeclaredMethod("calculateGrabMovementInfo", int.class);
            calculate.setAccessible(true);
            Object info = calculate.invoke(null, totalTicks);
            int delta = vector3$field(info, "grabbedDelta").getInt(info);
            int pivot = vector3$field(info, "grabbedScalePivotTick").getInt(info);
            float factor = vector3$field(info, "grabbedScaleFactor").getFloat(info);
            return tick -> Math.clamp(pivot >= 0 ? pivot + Math.round((tick - pivot) * factor) : tick + delta, 0, totalTicks);
        } catch (ReflectiveOperationException exception) {
            Vector3.LOGGER.warn("Couldn't read Flashback's keyframe drag, keeping ticks", exception);
            return tick -> tick;
        }
    }

    private static Field vector3$field(Object owner, String name) throws NoSuchFieldException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void vector3$dragGroup(float x, float mouseX) {
        if (vector3$draggedGroup == null) return;
        if (vector3$groupDrag == null) {
            PrefabGroups.Span span = vector3$span(vector3$draggedGroup);
            if (span == null) {
                vector3$draggedGroup = null;
                return;
            }
            vector3$groupDrag = new PrefabGroups.Drag(editorScene, span);
        }
        upgradeToSceneWrite();
        if (ImGui.isMouseClicked(1) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            vector3$groupDrag.cancel(editorScene);
            vector3$groupDrag = null;
            vector3$draggedGroup = null;
            vector3$keyframesChanged();
            return;
        }
        if (ImGui.isMouseDown(0)) {
            int delta = timelineXToReplayTick(mouseX - x) - timelineXToReplayTick(vector3$groupDragStartX - x);
            if (vector3$groupDrag.update(editorScene, delta)) vector3$keyframesChanged();
            return;
        }
        var entry = vector3$groupDrag.finish(editorScene);
        if (entry != null) editorScene.push(entry);
        vector3$groupDrag = null;
        vector3$draggedGroup = null;
        vector3$keyframesChanged();
    }

    private static void vector3$keyframesChanged() {
        editorState.markDirty();
        ((MinecraftExt) Minecraft.getInstance()).flashback$applyKeyframes();
    }

    private static int vector3$groupColour(String groupId) {
        int rgb = java.awt.Color.HSBtoRGB((groupId.hashCode() & 0xFFFF) / 65535f, 0.55f, 0.95f);
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    // Empty space in a group's rows counts too, but a click that would hit a keyframe stays Flashback's.
    private static String vector3$groupAt(float mouseX, float mouseY) {
        for (PrefabGroups.Handle handle : vector3$groupHandles) {
            if (mouseX < handle.left() || mouseX > handle.right() || mouseY < handle.top() || mouseY > handle.bottom()) continue;
            if (handle.row() < 0 || !vector3$nearKeyframe(handle.row(), mouseX)) return handle.groupId();
        }
        return null;
    }

    private static boolean vector3$nearKeyframe(int row, float mouseX) {
        if (row >= editorScene.keyframeTracks.size()) return false;
        TreeMap<Integer, Keyframe> keyframes = editorScene.keyframeTracks.get(row).keyframesByTick;
        int tick = timelineXToReplayTick(mouseX - vector3$timelineX);
        Map.Entry<Integer, Keyframe> floor = keyframes.floorEntry(tick);
        if (floor != null && floor.getValue().getCustomWidthInTicks() > 0
                && tick <= floor.getKey() + Math.ceil(floor.getValue().getCustomWidthInTicks())) return true;
        for (Integer key : new Integer[]{floor == null ? null : floor.getKey(), keyframes.ceilingKey(tick)}) {
            if (key != null && Math.abs(vector3$timelineX + replayTickToTimelineX(key) - mouseX) < keyframeSize) return true;
        }
        return false;
    }

    @Inject(method = "renderInner", at = @At("TAIL"))
    private static void vector3$syncSelectionAfterTimelineInput(CallbackInfo ci) {
        vector3$syncGizmoSelection();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void vector3$refreshShapesAfterTimelineUnlock(CallbackInfo ci) {
        ShapeManagerWindow.render();
        PrefabBasketWindow.render(!selectedKeyframesList.isEmpty());
        Vector3.PREFABS.renderPanel();
        if (ShapeTimelineSelection.consumeRefresh()) vector3$refreshKeyframes = true;
        if (editorState == null) return;
       
        if (vector3$lastEditorModCount != editorState.modCount) {
            vector3$lastEditorModCount = editorState.modCount;
            vector3$refreshKeyframes = true;
        }
        if (!vector3$refreshKeyframes) return;
        vector3$refreshKeyframes = false;
        editorState.applyKeyframes(ShapeKeyframeType.REFRESH_HANDLER, TimelineWindow.getCursorTick());
        vector3$pruneDeletedShapes();
    }

    private static void vector3$pruneDeletedShapes() {
        if (editorScene == null) return;
        Set<String> liveShapeIds = new HashSet<>();
        for (KeyframeTrack track : editorScene.keyframeTracks) {
            if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
            for (Keyframe keyframe : track.keyframesByTick.values()) {
                if (keyframe instanceof ShapeKeyframe shape) liveShapeIds.add(shape.value.shapeId());
            }
        }
        ShapeTrackRegistry.retainOnly(liveShapeIds);
    }

    // Inside Flashback's track popup, just above "Clear keyframes".
    @Inject(method = "renderKeyframeElements", at = @At(value = "CONSTANT", args = "stringValue=flashback.clear_keyframes"))
    private static void vector3$trackGroupItems(float x, float y, int cursorTicks, int middleX, CallbackInfo ci,
            @Local(name = "trackIndex") int trackIndex) {
        List<SelectedKeyframes> targets = vector3$groupTargets(trackIndex);
        String current = vector3$sharedGroup(targets);
        vector3$createGroupItem(trackIndex);
        List<PrefabGroups.Span> spans = PrefabGroups.spans(editorScene);
        if (!spans.isEmpty() && ImGui.beginMenu(I18n.get("vector3.prefab.track.add_to_group"))) {
            for (PrefabGroups.Span span : spans) {
                if (ImGui.menuItem(span.group().name() + "###" + span.group().id(), "", span.group().id().equals(current))) {
                    upgradeToSceneWrite();
                    PrefabGroups.tagSelection(editorScene, targets, span.group().id());
                    editorState.markDirty();
                }
            }
            ImGui.endMenu();
        }
        if (current != null && ImGui.menuItem(I18n.get("vector3.prefab.track.leave_group"))) {
            upgradeToSceneWrite();
            PrefabGroups.tagSelection(editorScene, targets, null);
            editorState.markDirty();
        }
    }

    private static String vector3$sharedGroup(List<SelectedKeyframes> targets) {
        String shared = null;
        for (SelectedKeyframes selected : targets) {
            if (selected.trackIndex() >= editorScene.keyframeTracks.size()) continue;
            TreeMap<Integer, Keyframe> keyframes = editorScene.keyframeTracks.get(selected.trackIndex()).keyframesByTick;
            for (int tick : selected.keyframeTicks()) {
                Keyframe keyframe = keyframes.get(tick);
                String group = keyframe == null ? null : PrefabGroups.groupOf(keyframe);
                if (group == null || (shared != null && !shared.equals(group))) return null;
                shared = group;
            }
        }
        return shared;
    }

    // The add-track menu's check (the first one is the per-track add button): one Skip track at most.
    @WrapOperation(method = "renderKeyframeElements", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/keyframe/KeyframeType;canBeCreatedNormally()Z"))
    private static boolean vector3$singleSkipTrack(KeyframeType<?> type, Operation<Boolean> original) {
        if (type == SkipKeyframeType.INSTANCE && editorScene != null) {
            for (KeyframeTrack track : editorScene.keyframeTracks) {
                if (track.keyframeType == SkipKeyframeType.INSTANCE) return false;
            }
        }
        return original.call(type);
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseClicked(I)Z"))
    private static boolean vector3$keepViewportClicksOutOfTimeline(int button) {
        boolean clicked = vector3$isMouseInTimeline() && ImGui.isMouseClicked(button);
        if (vector3$draggedGroup != null) return false;
        String group = clicked && button <= 1 ? vector3$groupAt(mouseX, mouseY) : null;
        if (group == null) return clicked;
        // Clicks on a group never reach Flashback: left drags the group, right opens its menu.
        if (button == 0 && vector3$draggedGroup == null) {
            vector3$draggedGroup = group;
            vector3$groupDragStartX = mouseX;
        } else if (button == 1) {
            vector3$menuGroup = group;
            vector3$openGroupMenu = true;
        }
        return false;
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseDragging(I)Z"))
    private static boolean vector3$keepViewportDragOutOfTimeline(int button) {
        if (Vector3.GIZMO_EDITOR.isDragging() || Vector3.ORBIT_GIZMO.isDragging() || Vector3.PREFABS.isDragging()
                || vector3$draggedGroup != null) {
            return false;
        }
        return ImGui.isMouseDragging(button);
    }

    private static void vector3$syncGizmoSelection() {
        if (Vector3.GIZMO_EDITOR.isDragging() || Vector3.ORBIT_GIZMO.isDragging() || Vector3.PREFABS.isDragging()) {
            return;
        }
        if (selectedKeyframesList.size() != 1
                || selectedKeyframesList.getFirst().keyframeTicks().size() != 1) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.clearSelection();
            return;
        }
        SelectedKeyframes selected = selectedKeyframesList.getFirst();
        int trackIndex = selected.trackIndex();
        if (trackIndex < 0 || trackIndex >= editorScene.keyframeTracks.size()) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.clearSelection();
            return;
        }
        int tick = selected.keyframeTicks().iterator().nextInt();
        Keyframe keyframe = editorScene.keyframeTracks.get(trackIndex).keyframesByTick.get(tick);
        if (keyframe instanceof CameraOrbitKeyframe orbitKeyframe) {
            Vector3.GIZMO_EDITOR.clearSelection();
            Vector3.ORBIT_GIZMO.select(orbitKeyframe, orbit -> {
                CameraOrbitKeyframe replacement = new CameraOrbitKeyframe(new Vector3d(orbit.center()),
                        (float) orbit.distance(), (float) orbit.yaw(), (float) orbit.pitch(),
                        orbitKeyframe.interpolationType());
                ((OrbitTilt) replacement).vector3$setTilt((float) orbit.tiltX(), (float) orbit.tiltZ());
                upgradeToSceneWrite();
                editorScene.setKeyframe(trackIndex, tick, replacement);
                EditorStateManager.getCurrent().markDirty();
            });
            return;
        }
        Vector3.ORBIT_GIZMO.clearSelection();
        if (selected.type() != ShapeKeyframeType.INSTANCE || !(keyframe instanceof ShapeKeyframe shape)) {
            Vector3.GIZMO_EDITOR.clearSelection();
            return;
        }
        Vector3.GIZMO_EDITOR.select(shape, replacement -> {
            upgradeToSceneWrite();
            editorScene.setKeyframe(trackIndex, tick,
                    new ShapeKeyframe(replacement, shape.interpolationType()));
            EditorStateManager.getCurrent().markDirty();
        });
    }

    private static boolean vector3$isMouseInTimeline() {
        return !ReplayUI.isMainFrameHovered()
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
