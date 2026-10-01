package ml.mypals.vectorthree.mixin.flashback;

import ml.mypals.vectorthree.fb.editor.EditorInput;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import ml.mypals.vectorthree.fb.editor.PropertiesWindow;
import ml.mypals.vectorthree.fb.shape.ShapeManagerWindow;
import ml.mypals.vectorthree.fb.timeline.ChannelTimeline;
import ml.mypals.vectorthree.fb.timeline.ClipTimeline;
import ml.mypals.vectorthree.fb.timeline.PropertiesPanel;
import ml.mypals.vectorthree.fb.timeline.ShapeTimeline;
import ml.mypals.vectorthree.fb.timeline.Timeline;
import ml.mypals.vectorthree.fb.timeline.TrackTimeline;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Flashback's timeline: the injection points only. What they do lives in fb/timeline. */
@Mixin(value = TimelineWindow.class, remap = false)
public abstract class TimelineWindowMixin {
    // Flashback for 26.2 passes GLFW key codes to ImGui.isKeyDown.
    @Unique private static final int GLFW_KEY_LEFT_CONTROL = 341, GLFW_KEY_LEFT_ALT = 342, GLFW_KEY_RIGHT_ALT = 346;

    @Inject(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;beginPopup(Ljava/lang/String;)Z",
            shift = At.Shift.BEFORE))
    private static void vector3$selectClickedShape(CallbackInfo ci) {
        ShapeTimeline.frame();
    }

    // Every track row is laid out from contentY, so moving it down frees a band above the top track for the Clips track.
    @ModifyVariable(method = "renderInner", at = @At("STORE"), name = "contentY")
    private static float vector3$clipBand(float contentY) {
        return ClipTimeline.band(contentY);
    }

    @ModifyVariable(method = "renderInner", at = @At("STORE"), name = "totalTrackHeight")
    private static float vector3$clipBandHeight(float totalTrackHeight) {
        return ClipTimeline.bandHeight(totalTrackHeight);
    }

    // Until they are composed, clips can reach past the end of the open replay; the timeline stretches to show them.
    @WrapOperation(method = {"renderInner", "handleClick"}, at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/playback/ReplayServer;getTotalReplayTicks()I"))
    private static int vector3$timelineCoversClips(ReplayServer server, Operation<Integer> original) {
        int total = Math.max(original.call(server), ClipTimeline.end());
        // A project's timeline keeps going past its last clip (the world holds still there), a minute at a time.
        return ml.mypals.vectorthree.fb.clips.ProjectClock.active() ? total + 1200 : total;
    }

    // Both the drag preview and the drop read this, so a single dragged clip visibly snaps while it moves.
    @Inject(method = "calculateGrabMovementInfo", at = @At("RETURN"))
    private static void vector3$snapGrabbedClip(int totalTicks, CallbackInfoReturnable<Object> cir) {
        ClipTimeline.snapMovement(cir.getReturnValue());
    }

    // Flashback previews keyframes while scrubbing the playhead only with Ctrl held; instant preview always does.
    // renderInner reads Ctrl as ImGui.isKeyDown(GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW_KEY_RIGHT_CONTROL).
    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;isKeyDown(I)Z"))
    private static boolean vector3$scrubWithKeyframes(int key, Operation<Boolean> original) {
        return original.call(key) || key == GLFW_KEY_LEFT_CONTROL && ShapeManagerWindow.isInstantPreview();
    }

    // Flashback's keyframe popup becomes the Properties window: begin/end are swapped for the window's, and
    // the popup follows the selection instead of needing a right click.
    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;beginPopup(Ljava/lang/String;)Z"))
    private static boolean vector3$beginProperties(String id, Operation<Boolean> original) {
        return PropertiesPanel.begin(id, key -> original.call(key));
    }

    @WrapOperation(method = "renderInner", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;renderKeyframeOptionsPopup(I)V"))
    private static void vector3$propertiesPage(int totalTicks, Operation<Void> original) {
        PropertiesPanel.page(totalTicks, () -> original.call(totalTicks));
    }

    // "Custom" joins the interpolation types: choosing it gives the selected keyframes a speed curve.
    @WrapOperation(method = "renderKeyframeOptionsPopup", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;combo(Ljava/lang/String;[I[Ljava/lang/String;)Z"))
    private static boolean vector3$customInterpolation(String label, int[] selected, String[] names,
            Operation<Boolean> original) {
        return PropertiesPanel.customInterpolation(label, selected, names, (l, s, n) -> original.call(l, s, n));
    }

    // The keyframes Flashback writes for the new interpolation type are copies, still carrying the curve.
    @WrapOperation(method = "renderKeyframeOptionsPopup", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/moulberry/flashback/state/EditorScene;push(Lcom/moulberry/flashback/state/EditorSceneHistoryEntry;)V"))
    private static void vector3$dropCurvesWithType(EditorScene scene, EditorSceneHistoryEntry entry, Operation<Void> original) {
        PropertiesPanel.beforePush(entry);
        original.call(scene, entry);
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE", ordinal = 0, target = "Limgui/moulberry90/ImGui;endPopup()V"),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;renderKeyframeOptionsPopup(I)V")))
    private static void vector3$endProperties() {
        PropertiesWindow.end();
    }

    // Keyframes keying only some channels have the lower half of their diamond darkened; hovering lists the channels.
    @Inject(method = "renderKeyframes", at = @At("RETURN"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void vector3$drawPartialKeyframes(float x, float y, float mouseX, int minTicks, float availableTicks,
            int totalTicks, CallbackInfo ci) {
        ChannelTimeline.drawPartialKeyframes(x, y);
    }

    // Flashback only tests the mouse position, so clicks on a floating window over the timeline fell through.
    @WrapOperation(method = {"renderInner", "handleClick"}, at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;isMouseClicked(I)Z"))
    private static boolean vector3$clickOnTimelineOnly(int button, Operation<Boolean> original) {
        return original.call(button) && ImGui.isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
    }

    @WrapOperation(method = "handleClick", at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;openPopup(Ljava/lang/String;)V"))
    private static void vector3$openProperties(String id, Operation<Void> original) {
        if (PropertiesWindow.KEYFRAME_POPUP.equals(id)) PropertiesWindow.requestFocus();
        else original.call(id);
    }

    // Flashback's box select feeds window-absolute X where every other caller passes it relative to the window.
    @ModifyArg(method = "releaseGrabbed", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineXToReplayTick(F)I"))
    private static float vector3$boxSelectStart(float timelineX) {
        return timelineX - Timeline.x();
    }

    @ModifyArg(method = "releaseGrabbed", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineXToReplayTick(F)I"))
    private static float vector3$boxSelectEnd(float timelineX) {
        return timelineX - Timeline.x();
    }

    @Inject(method = "releaseGrabbed", at = @At("RETURN"))
    private static void vector3$pushClipsAside(CallbackInfo ci) {
        ClipTimeline.pushClipsAside();
    }

    @Inject(method = "handleClick", at = @At("HEAD"))
    private static void vector3$rememberSelection(CallbackInfo ci) {
        TrackTimeline.rememberSelection();
    }

    @Inject(method = "handleClick", at = @At("RETURN"))
    private static void vector3$restoreSelection(CallbackInfo ci) {
        TrackTimeline.restoreSelection();
    }

    @Inject(method = "renderKeyframeElements", at = @At(value = "CONSTANT", args = "stringValue=flashback.create_keyframe_at_n"))
    private static void vector3$createKeyframePopupItems(float x, float y, int cursorTicks, int middleX, CallbackInfo ci) {
        TrackTimeline.createKeyframePopupItems();
    }

    @Inject(method = "renderKeyframes", at = @At("HEAD"))
    private static void vector3$drawPrefabGroups(float x, float y, float mouseX, int minTicks, float availableTicks,
            int totalTicks, CallbackInfo ci) {
        TrackTimeline.drawOverlays(x, y, mouseX);
    }

    // Dropping on another row of the same type moves the keyframes there instead of Flashback's in-track move.
    @Inject(method = "releaseGrabbed", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow$GrabMovementInfo;grabbedScalePivotTick:I"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void vector3$moveAcrossTracks(ReplayServer server, int totalTicks, float rowsY, CallbackInfo ci,
            @Coerce GrabMovementInfoAccessor movement) {
        TrackTimeline.moveAcrossTracks(totalTicks, rowsY, movement);
    }

    // Flashback scales the dragged keyframes about a pivot while Alt is held; Alt now copies, so scaling moves to Ctrl+Alt.
    // calculateGrabMovementInfo reads Alt as ImGui.isKeyDown(GLFW_KEY_LEFT_ALT) / ImGui.isKeyDown(GLFW_KEY_RIGHT_ALT).
    @WrapOperation(method = "calculateGrabMovementInfo", at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;isKeyDown(I)Z"))
    private static boolean vector3$scaleWithCtrlAlt(int key, Operation<Boolean> original) {
        boolean down = original.call(key);
        return key == GLFW_KEY_LEFT_ALT || key == GLFW_KEY_RIGHT_ALT ? down && EditorInput.isCtrlDown() : down;
    }

    // Grabbing a selected track's handle drags the whole selection; Alt leaves a copy of the track behind instead,
    // and the original is the one being dragged.
    @Inject(method = "renderKeyframeElements", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;repositioningKeyframeTrack:I"))
    private static void vector3$copyDraggedTrack(float x, float y, int cursorTicks, int middleX, CallbackInfo ci) {
        TrackTimeline.copyDraggedTrack();
    }

    // Flashback moves a dragged track up while its index is above 0; with the Clips track on top, row 1 is the ceiling.
    @ModifyExpressionValue(method = "renderInner", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, ordinal = 8,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;repositioningKeyframeTrack:I"))
    private static int vector3$clipsCeiling(int index) {
        return ClipTimeline.belowClips(index);
    }

    @ModifyExpressionValue(method = "renderInner", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, ordinal = 9,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;repositioningKeyframeTrack:I"))
    private static int vector3$clipsCeilingAgain(int index) {
        return ClipTimeline.belowClips(index);
    }

    // The track list: click or box-select tracks, drag the selection by a handle, Delete removes it.
    @Inject(method = "renderKeyframeElements", at = @At("TAIL"))
    private static void vector3$selectTracks(float panelX, float panelY, int cursorTicks, int middleX, CallbackInfo ci) {
        TrackTimeline.selectTracks(panelX, panelY, middleX);
    }

    @Inject(method = "renderInner", at = @At("TAIL"))
    private static void vector3$syncSelectionAfterTimelineInput(CallbackInfo ci) {
        ShapeTimeline.syncGizmoSelection();
    }

    // The same drag offset Flashback draws the grabbed keyframes with this frame.
    @Inject(method = "renderKeyframes", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, ordinal = 0,
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;timelineWidth:F"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private static void vector3$captureGrab(float x, float y, float mouseX, int minTicks, float availableTicks,
            int totalTicks, CallbackInfo ci, float lineHeight, ImDrawList drawList,
            @Coerce GrabMovementInfoAccessor movement) {
        ShapeTimeline.captureGrab(movement, totalTicks);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void vector3$refreshShapesAfterTimelineUnlock(CallbackInfo ci) {
        ShapeTimeline.afterRender();
    }

    // Inside Flashback's track popup, just above "Clear keyframes".
    @Inject(method = "renderKeyframeElements", at = @At(value = "CONSTANT", args = "stringValue=flashback.clear_keyframes"))
    private static void vector3$trackGroupItems(float x, float y, int cursorTicks, int middleX, CallbackInfo ci,
            @Local(name = "trackIndex") int trackIndex) {
        TrackTimeline.trackPopupItems(trackIndex);
    }

    // Ctrl+Delete: delete the selection and pull everything after it back by the time it took up.
    @WrapOperation(method = "handleKeyPresses", at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;removeAllSelectedKeyframes()V"))
    private static void vector3$rippleDelete(Operation<Void> original) {
        TrackTimeline.ripple(() -> original.call());
    }

    @Inject(method = "handleKeyPresses", at = @At("HEAD"))
    private static void vector3$protectLockedTracks(ReplayServer server, int currentTick, int totalTicks, CallbackInfo ci) {
        TrackTimeline.protectLocked();
    }

    // The add-track menu's check (the first one is the per-track add button): one Skip and one Loop track at most.
    @WrapOperation(method = "renderKeyframeElements", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/moulberry/flashback/keyframe/KeyframeType;canBeCreatedNormally()Z"))
    private static boolean vector3$singleSkipTrack(KeyframeType<?> type, Operation<Boolean> original) {
        return TrackTimeline.singleSkipTrack(type, key -> original.call(key));
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseClicked(I)Z"))
    private static boolean vector3$keepViewportClicksOutOfTimeline(int button) {
        return TrackTimeline.filterClick(button);
    }

    @Redirect(method = "renderInner", at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;isMouseDragging(I)Z"))
    private static boolean vector3$keepViewportDragOutOfTimeline(int button) {
        return TrackTimeline.filterDrag(button);
    }
}
