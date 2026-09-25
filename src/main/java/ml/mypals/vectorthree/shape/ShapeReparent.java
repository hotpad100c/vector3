package ml.mypals.vectorthree.shape;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.ShapeKeyframeType;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeChange;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ShapeReparent {
    private ShapeReparent() {}

    public static boolean canParent(String child, @Nullable String parent) {
        for (String current = parent; current != null && !current.isEmpty(); ) {
            if (current.equals(child)) return false;
            ShapeState state = ShapeTrackRegistry.state(current);
            current = state == null ? null : state.parentShapeId();
        }
        return true;
    }

    public static void reparent(EditorState editorState, String shapeId, @Nullable String newParentId) {
        String target = newParentId == null ? "" : newParentId;
        if (!canParent(shapeId, target)) return;
        long stamp = editorState.acquireWrite();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            List<EditorSceneHistoryAction> undo = new ArrayList<>(), redo = new ArrayList<>();
            for (int index = 0; index < scene.keyframeTracks.size(); index++) {
                KeyframeTrack track = scene.keyframeTracks.get(index);
                if (track.keyframeType != ShapeKeyframeType.INSTANCE) continue;
                for (Map.Entry<Integer, Keyframe> entry : track.keyframesByTick.entrySet()) {
                    if (!(entry.getValue() instanceof ShapeKeyframe keyframe) || !keyframe.value.shapeId().equals(shapeId)) continue;
                    ShapeState state = keyframe.value;
                    if (state.mount() == null && Objects.equals(emptyToNull(state.parentShapeId()), emptyToNull(target))) continue;
                    int tick = entry.getKey();
                    // A mount's entity pose at other ticks isn't known, so mounted keyframes convert from its pose now.
                    Matrix4f world = (state.mount() != null ? ShapeTrackRegistry.parentTransform(state)
                            : worldAt(scene, state.parentShapeId(), tick, new HashSet<>())).mul(local(state));
                    Matrix4f local = worldAt(scene, target, tick, new HashSet<>(Set.of(shapeId))).invert().mul(world);
                    Vector3f position = local.getTranslation(new Vector3f());
                    Vector3f scale = local.getScale(new Vector3f());
                    Vector3f euler = local.getNormalizedRotation(new Quaternionf()).getEulerAnglesXYZ(new Vector3f());
                    ShapeKeyframe replacement = (ShapeKeyframe) keyframe.copy();
                    replacement.value = state.withMount(null).withParent(target).withTransform(position.x, position.y, position.z,
                            (float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z),
                            scale.x, scale.y, scale.z);
                    redo.add(new EditorSceneHistoryAction.SetKeyframe(ShapeKeyframeType.INSTANCE, index, tick, replacement));
                    undo.add(new EditorSceneHistoryAction.SetKeyframe(ShapeKeyframeType.INSTANCE, index, tick, keyframe.copy()));
                }
            }
            if (redo.isEmpty()) return;
            scene.push(new EditorSceneHistoryEntry(undo, redo,
                    I18n.get("vector3.history.reparent", ShapeTrackRegistry.displayName(shapeId))));
        } finally {
            editorState.release(stamp);
        }
        editorState.markDirty();
    }

    private static Matrix4f worldAt(EditorScene scene, @Nullable String shapeId, int tick, Set<String> visited) {
        if (shapeId == null || shapeId.isEmpty() || !visited.add(shapeId)) return new Matrix4f();
        ShapeState state = stateAt(scene, shapeId, tick);
        if (state == null) return new Matrix4f();
        if (state.mount() != null) return ShapeTrackRegistry.parentTransform(state).mul(local(state));
        return worldAt(scene, state.parentShapeId(), tick, visited).mul(local(state));
    }

    /** The shape's interpolated state at {@code tick}, the same way its track would apply it. */
    public static @Nullable ShapeState stateAt(EditorScene scene, String shapeId, float tick) {
        for (KeyframeTrack track : scene.keyframeTracks) {
            if (track.keyframeType != ShapeKeyframeType.INSTANCE || !track.enabled || track.keyframesByTick.isEmpty()) continue;
            if (!(track.keyframesByTick.firstEntry().getValue() instanceof ShapeKeyframe first)
                    || !first.value.shapeId().equals(shapeId)) continue;
            if (track.createKeyframeChange(tick, null) instanceof CustomKeyframeChange change
                    && change.value() instanceof ShapeState state) return state;
        }
        return null;
    }

    private static Matrix4f local(ShapeState state) {
        return new Matrix4f()
                .translate((float) state.x(), (float) state.y(), (float) state.z())
                .rotate(new Quaternionf().rotateXYZ((float) Math.toRadians(state.pitch()),
                        (float) Math.toRadians(state.yaw()), (float) Math.toRadians(state.roll())))
                .scale((float) state.scaleX(), (float) state.scaleY(), (float) state.scaleZ());
    }

    private static @Nullable String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
