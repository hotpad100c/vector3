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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
    private static final List<PrefabGroups.Handle> vector3$groupHandles = new ArrayList<>();
    private static String vector3$draggedGroup;
    private static float vector3$groupDragStartX;
    private static PrefabGroups.Drag vector3$groupDrag;
    private static float vector3$timelineX;
    private static String vector3$menuGroup;
    private static boolean vector3$openGroupMenu;
    private static final ImString vector3$groupName = new ImString(128);

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

    private static void vector3$handlePrefabs() {
        if (ImGui.getDragDropPayload(PrefabBasketWindow.PAYLOAD) instanceof String id
                && ImGui.isMouseReleased(0) && vector3$isMouseInTimeline()) {
            PrefabBasketWindow.place(id, timelineXToReplayTick(mouseX - x));
        }
        String atCursor = PrefabBasketWindow.consumePlaceAtCursor();
        if (atCursor != null) PrefabBasketWindow.place(atCursor, TimelineWindow.getCursorTick());
        if (PrefabBasketWindow.consumeSaveRequest()) Vector3.PREFABS.beginSave(editorScene, selectedKeyframesList);
        Vector3.PREFABS.frame(editorScene, editorState, () -> upgradeToSceneWrite());
        vector3$groupMenu();
    }

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

    private static PrefabGroups.Span vector3$span(String groupId) {
        if (groupId == null || editorScene == null) return null;
        for (PrefabGroups.Span span : PrefabGroups.spans(editorScene)) {
            if (span.group().id().equals(groupId)) return span;
        }
        return null;
    }

    // Under the keyframes: a tint over each of the group's rows, and a bar along the top of its first
    // row that drags the whole group.
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

    // The keyframes follow the mouse; the drop is pushed as a single undoable move.
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

    // Mirrors handleClick's pick: a keyframe within keyframeSize pixels, or one whose own width covers the tick.
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
        // Every scene edit (undo/redo, delete, drag, popup edits) bumps modCount through markDirty().
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
        KeyframeTrack track = editorScene.keyframeTracks.get(trackIndex);
        String current = PrefabGroups.groupOf(track);
        if (ImGui.menuItem("\ue945 " + I18n.get("vector3.prefab.track.create_group"))) {
            Set<Integer> tracks = new java.util.TreeSet<>();
            tracks.add(trackIndex);
            for (SelectedKeyframes selected : selectedKeyframesList) tracks.add(selected.trackIndex());
            upgradeToSceneWrite();
            PrefabGroups.create(editorScene, I18n.get("vector3.prefab.group.default_name",
                    PrefabGroups.groups(editorScene).size() + 1), tracks);
            editorState.markDirty();
        }
        List<PrefabGroups.Span> spans = PrefabGroups.spans(editorScene);
        if (!spans.isEmpty() && ImGui.beginMenu(I18n.get("vector3.prefab.track.add_to_group"))) {
            for (PrefabGroups.Span span : spans) {
                if (ImGui.menuItem(span.group().name() + "###" + span.group().id(), "", span.group().id().equals(current))) {
                    upgradeToSceneWrite();
                    PrefabGroups.tag(track, span.group().id());
                    editorState.markDirty();
                }
            }
            ImGui.endMenu();
        }
        if (current != null && ImGui.menuItem(I18n.get("vector3.prefab.track.leave_group"))) {
            upgradeToSceneWrite();
            PrefabGroups.tag(track, null);
            editorState.markDirty();
        }
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
