package ml.mypals.vectorthree.prefab;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.CameraOrbitKeyframe;
import ml.mypals.vectorthree.camera.dolly.DollyZoom;
import ml.mypals.vectorthree.camera.dolly.DollyZoomKeyframeType;
import ml.mypals.vectorthree.camera.lookto.LookTo;
import ml.mypals.vectorthree.camera.lookto.LookToKeyframeType;
import ml.mypals.vectorthree.camera.orbit.OrbitMath;
import ml.mypals.vectorthree.camera.orbit.OrbitTilt;
import ml.mypals.vectorthree.camera.target.Target;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframe;
import ml.mypals.vectorthree.shape.ShapeState;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Moves the coordinate fields of a prefab's keyframes between prefab space and the world. */
public final class PrefabCoordinates {
    private PrefabCoordinates() {}

    /** Places a prefab: every shape gets a fresh id, so the same prefab can be placed twice. */
    public static Prefab toWorld(Prefab prefab, PrefabTransform transform) {
        Map<String, String> ids = new HashMap<>();
        for (String id : shapeIds(prefab)) ids.put(id, "vector3:timeline/" + UUID.randomUUID());
        return map(prefab, transform, 0, eyeHeight(), ids);
    }

    /** Expresses world keyframes relative to {@code origin}. */
    public static Prefab toPrefab(Prefab world, PrefabTransform origin) {
        return map(world, origin.inverse(), eyeHeight(), 0, Map.of());
    }

    private static Prefab map(Prefab prefab, PrefabTransform transform, double eyeIn, double eyeOut, Map<String, String> ids) {
        Set<String> contained = shapeIds(prefab);
        List<Prefab.Track> tracks = new ArrayList<>();
        for (Prefab.Track track : prefab.tracks()) {
            TreeMap<Integer, Keyframe> keyframes = new TreeMap<>();
            track.keyframes().forEach((tick, keyframe) ->
                    keyframes.put(tick, map(keyframe, transform, eyeIn, eyeOut, ids, contained)));
            tracks.add(new Prefab.Track(track.type(), track.customName(), track.customColour(), keyframes));
        }
        return new Prefab(prefab.name(), tracks);
    }

    private static Keyframe map(Keyframe keyframe, PrefabTransform t, double eyeIn, double eyeOut,
            Map<String, String> ids, Set<String> contained) {
        if (keyframe instanceof CameraKeyframe camera) {
            Vector3d eye = t.point(new Vector3d(camera.position).add(0, eyeIn, 0));
            float[] view = t.view(camera.yaw, camera.pitch, camera.roll);
            return new CameraKeyframe(eye.sub(0, eyeOut, 0), view[0], view[1], view[2], camera.interpolationType());
        }
        if (keyframe instanceof CameraOrbitKeyframe orbit) return mapOrbit(orbit, t);
        if (keyframe instanceof ShapeKeyframe shape) {
            return new ShapeKeyframe(mapShape(shape.value, t, ids, contained), shape.interpolationType());
        }
        if (keyframe instanceof CustomKeyframe<?> custom && custom.value instanceof LookTo look) {
            return new CustomKeyframe<>(LookToKeyframeType.INSTANCE, look.withTarget(mapTarget(look.target(), t, ids)),
                    custom.interpolationType());
        }
        if (keyframe instanceof CustomKeyframe<?> custom && custom.value instanceof DollyZoom dolly) {
            float[] view = t.view(dolly.yaw(), dolly.pitch(), 0);
            DollyZoom mapped = dolly.withTarget(mapTarget(dolly.target(), t, ids)).withShot(
                    (float) (dolly.distance() * t.scale()), (float) (dolly.frameHeight() * t.scale()), view[0], view[1]);
            return new CustomKeyframe<>(DollyZoomKeyframeType.INSTANCE, mapped, custom.interpolationType());
        }
        return keyframe.copy();
    }

    /** Position targets move with the prefab and shape targets follow the placed copies; entities stay. */
    private static Target mapTarget(Target target, PrefabTransform t, Map<String, String> ids) {
        if (target.kind() == Target.Kind.POSITION) {
            Vector3d position = t.point(new Vector3d(target.x(), target.y(), target.z()));
            target = target.withPosition(position.x, position.y, position.z);
        }
        if (target.shapeId() != null && ids.containsKey(target.shapeId())) target = target.withShapeId(ids.get(target.shapeId()));
        return target;
    }

    // Exact: the orbit plane's normal and the camera's offset are rotated, then split back into tilt and yaw/pitch.
    private static Keyframe mapOrbit(CameraOrbitKeyframe orbit, PrefabTransform t) {
        OrbitTilt tilt = (OrbitTilt) orbit;
        Vector3d normal = t.direction(OrbitMath.tilt(tilt.vector3$tiltX(), tilt.vector3$tiltZ()).transform(new Vector3d(0, 1, 0)));
        double[] newTilt = OrbitMath.tiltOf(normal);
        Vector3d offset = t.direction(OrbitMath.eyeOffset(orbit.yaw, orbit.pitch, orbit.distance,
                tilt.vector3$tiltX(), tilt.vector3$tiltZ())).mul(t.scale());
        double[] angles = OrbitMath.orbitOf(offset, newTilt[0], newTilt[1], orbit.yaw);
        CameraOrbitKeyframe mapped = new CameraOrbitKeyframe(t.point(orbit.center), (float) (orbit.distance * t.scale()),
                (float) angles[0], (float) angles[1], orbit.interpolationType());
        ((OrbitTilt) mapped).vector3$setTilt((float) newTilt[0], (float) newTilt[1]);
        return mapped;
    }

    // Children of shapes inside the prefab keep their local transform; points are local, and an
    // area's corners name the recorded blocks it copies, so neither moves.
    private static ShapeState mapShape(ShapeState state, PrefabTransform t, Map<String, String> ids, Set<String> contained) {
        String parent = state.parentShapeId() == null ? "" : state.parentShapeId();
        if (!contained.contains(parent) && state.mount() == null) {
            Vector3d position = t.point(new Vector3d(state.x(), state.y(), state.z()));
            Quaternionf rotation = new Quaternionf(t.rotation()).mul(new Quaternionf().rotateXYZ(
                    (float) Math.toRadians(state.pitch()), (float) Math.toRadians(state.yaw()), (float) Math.toRadians(state.roll())));
            Vector3f euler = rotation.getEulerAnglesXYZ(new Vector3f());
            state = state.withTransform(position.x, position.y, position.z, (float) Math.toDegrees(euler.x),
                    (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z),
                    state.scaleX() * t.scale(), state.scaleY() * t.scale(), state.scaleZ() * t.scale());
        }
        if (ids.containsKey(state.shapeId())) state = state.withIdentity(state.shapeType(), ids.get(state.shapeId()));
        if (ids.containsKey(parent)) state = state.withParent(ids.get(parent));
        return state;
    }

    private static Set<String> shapeIds(Prefab prefab) {
        Set<String> ids = new HashSet<>();
        for (Prefab.Track track : prefab.tracks()) {
            for (Keyframe keyframe : track.keyframes().values()) {
                if (keyframe instanceof ShapeKeyframe shape) ids.add(shape.value.shapeId());
            }
        }
        return ids;
    }

    static double eyeHeight() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getEyeHeight() : 1.62;
    }
}
