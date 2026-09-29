package ml.mypals.vectorthree.expression;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.expression.lang.ExprError;
import ml.mypals.vectorthree.expression.lang.Value;
import ml.mypals.vectorthree.flashback.ShapeKeyframe;
import ml.mypals.vectorthree.flashback.custom.CustomKeyframeType;
import ml.mypals.vectorthree.multiedit.MultiEditSession;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/** What expressions can read besides time: other tracks' parameters, entities and the camera. */
final class References {
    private References() {}

    static Value track(String name, float tick) {
        EditorScene scene = EvalContext.currentScene();
        if (scene == null) throw new ExprError("tracks can't be read here");
        EvalContext context = EvalContext.current();
        KeyframeTrack track = context != null ? context.tracks.computeIfAbsent(name, key -> find(scene, key)) : find(scene, name);
        if (track == null) throw new ExprError("no track named \"" + name + "\"");
        return new TrackRef(track, name, tick);
    }

    private static @Nullable KeyframeTrack find(EditorScene scene, String name) {
        for (KeyframeTrack track : scene.keyframeTracks) if (name.equals(track.customName)) return track;
        for (KeyframeTrack track : scene.keyframeTracks) if (name.equalsIgnoreCase(track.customName)) return track;
        for (KeyframeTrack track : scene.keyframeTracks) {
            Keyframe first = track.keyframesByTick.isEmpty() ? null : track.keyframesByTick.firstEntry().getValue();
            if (first instanceof ShapeKeyframe shape && (name.equalsIgnoreCase(shape.value.name())
                    || name.equalsIgnoreCase(ShapeTrackRegistry.displayName(shape.value.shapeId())))) {
                return track;
            }
        }
        for (KeyframeTrack track : scene.keyframeTracks) {
            if (name.equalsIgnoreCase(track.keyframeType.name()) || name.equalsIgnoreCase(track.keyframeType.id())) return track;
        }
        return null;
    }

    static String namespace(KeyframeType<?> type) {
        return type instanceof CustomKeyframeType<?> ? "vector3" : "flashback";
    }

    /** Every widget of the track's editor at {@code tick}, "label#n" to snapshot. */
    static Map<String, Object> capture(KeyframeTrack track, String name, float tick) {
        EvalContext context = EvalContext.current();
        boolean memo = context != null && context.tick == tick;
        if (memo && context.captures.containsKey(track)) return context.captures.get(track);
        if (ExpressionRuntime.active(track)) throw new ExprError("circular reference to track \"" + name + "\"");
        KeyframeChange change = track.createKeyframeChange(tick, null);
        Keyframe keyframe = change == null ? null : KeyframeRebuild.of(track, change);
        if (keyframe == null) {
            Entry<Integer, Keyframe> nearest = track.keyframesByTick.floorEntry((int) Math.floor(tick));
            if (nearest == null) nearest = track.keyframesByTick.firstEntry();
            keyframe = nearest == null ? null : nearest.getValue().copy();
        }
        Map<String, Object> captured = new LinkedHashMap<>();
        Keyframe target = keyframe;
        if (target != null) {
            ExpressionRuntime.runEditor(() -> captured.putAll(MultiEditSession.capture(() -> target.renderEditKeyframe(edit -> {}), false)));
        }
        if (memo) context.captures.put(track, captured);
        return captured;
    }

    private record TrackRef(KeyframeTrack track, String name, float tick) implements Value.Obj {
        @Override
        public Value member(String member) {
            return read(capture(track, name, tick), namespace(track.keyframeType), member, describe());
        }

        @Override
        public String describe() {
            return "track \"" + name + "\"";
        }
    }

    private static Value read(Map<String, Object> captured, String namespace, String member, String owner) {
        Map<String, String> aliases = WidgetKeys.aliases(captured.keySet(), namespace);
        String key = aliases.get(member);
        if (key == null) {
            throw new ExprError(owner + " has no ." + member + " (it has: "
                    + String.join(", ", aliases.keySet().stream().limit(16).toList()) + ")");
        }
        Value value = ExpressionRuntime.own(captured.get(key), ExpressionBinding.ALL);
        if (value == null) throw new ExprError("." + member + " can't be read");
        return value;
    }

    /** The keyframe being driven, as its keyframes have it before any expression; read at most once. */
    static final class SelfRef implements Value.Obj {
        private final Keyframe keyframe;
        private final String namespace;
        private @Nullable Map<String, Object> captured;

        SelfRef(Keyframe keyframe, KeyframeType<?> type) {
            this.keyframe = keyframe;
            this.namespace = namespace(type);
        }

        @Override
        public Value member(String member) {
            if (captured == null) {
                Map<String, Object> result = new LinkedHashMap<>();
                ExpressionRuntime.runEditor(() -> result.putAll(MultiEditSession.capture(() -> keyframe.renderEditKeyframe(edit -> {}), false)));
                captured = result;
            }
            return read(captured, namespace, member, "self");
        }

        @Override
        public String describe() {
            return "self";
        }
    }

    static Value entity(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) throw new ExprError("no world");
        UUID uuid = null;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
        }
        Entity found = null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (uuid != null ? uuid.equals(entity.getUUID()) : id.equalsIgnoreCase(entity.getName().getString())) {
                found = entity;
                break;
            }
        }
        if (found == null) throw new ExprError("no entity \"" + id + "\"");
        return new EntityRef(found);
    }

    private record EntityRef(Entity entity) implements Value.Obj {
        @Override
        public Value member(String name) {
            Vec3 position = entity.getPosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
            return switch (name) {
                case "x" -> Value.of(position.x);
                case "y" -> Value.of(position.y);
                case "z" -> Value.of(position.z);
                case "position" -> Value.vec(position.x, position.y, position.z);
                case "yaw" -> Value.of(entity.getYRot());
                case "pitch" -> Value.of(entity.getXRot());
                case "head_yaw" -> Value.of(entity.getYHeadRot());
                case "name" -> Value.text(entity.getName().getString());
                case "uuid" -> Value.text(entity.getUUID().toString());
                default -> throw new ExprError("an entity has no ." + name
                        + " (it has: x, y, z, position, yaw, pitch, head_yaw, name, uuid)");
            };
        }

        @Override
        public String describe() {
            return "entity \"" + entity.getName().getString() + "\"";
        }
    }

    static Value camera() {
        return CameraRef.INSTANCE;
    }

    private enum CameraRef implements Value.Obj {
        INSTANCE;

        @Override
        public Value member(String name) {
            Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            Vec3 position = camera.position();
            return switch (name) {
                case "x" -> Value.of(position.x);
                case "y" -> Value.of(position.y);
                case "z" -> Value.of(position.z);
                case "position" -> Value.vec(position.x, position.y, position.z);
                case "yaw" -> Value.of(camera.yRot());
                case "pitch" -> Value.of(camera.xRot());
                case "fov" -> Value.of(camera.getFov());
                default -> throw new ExprError("the camera has no ." + name + " (it has: x, y, z, position, yaw, pitch, fov)");
            };
        }

        @Override
        public String describe() {
            return "the camera";
        }
    }
}
