package ml.mypals.vectorthree.fb.expression;

import ml.mypals.vectorthree.core.expression.EntityFields;
import ml.mypals.vectorthree.core.expression.ExpressionBinding;
import ml.mypals.vectorthree.mc.expression.WidgetKeys;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.KeyframeTrack;
import ml.mypals.vectorthree.core.expression.lang.ExprError;
import ml.mypals.vectorthree.core.expression.lang.Value;
import ml.mypals.vectorthree.fb.shape.ShapeKeyframe;
import ml.mypals.vectorthree.fb.custom.CustomKeyframeType;
import ml.mypals.vectorthree.fb.multiedit.MultiEditSession;
import ml.mypals.vectorthree.mc.shape.ShapeTrackRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** What expressions can read besides time: other tracks' parameters, entities and the camera. */
final class References {
    private References() {}

    /** A track's editor widgets at a tick ("label#n" to snapshot) and the keyframe they were read from. */
    record Snapshot(Map<String, Object> values, @Nullable Keyframe keyframe) {}

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

    static Snapshot capture(KeyframeTrack track, String name, float tick) {
        EvalContext context = EvalContext.current();
        boolean memo = context != null && context.tick == tick;
        if (memo && context.captures.containsKey(track)) return context.captures.get(track);
        if (ExpressionRuntime.active(track)) {
            throw new ExprError("circular reference to track \"" + name + "\" (use self for the track's own parameters)");
        }
        KeyframeChange change = track.createKeyframeChange(tick, null);
        Keyframe keyframe = change == null ? null : KeyframeRebuild.of(track, change);
        if (keyframe == null) {
            Entry<Integer, Keyframe> nearest = track.keyframesByTick.floorEntry((int) Math.floor(tick));
            if (nearest == null) nearest = track.keyframesByTick.firstEntry();
            keyframe = nearest == null ? null : nearest.getValue().copy();
        }
        Snapshot snapshot = new Snapshot(keyframe == null ? Map.of() : read(keyframe), keyframe);
        if (memo) context.captures.put(track, snapshot);
        return snapshot;
    }

    private static Map<String, Object> read(Keyframe keyframe) {
        Map<String, Object> captured = new LinkedHashMap<>();
        ExpressionRuntime.runEditor(() -> captured.putAll(MultiEditSession.capture(() -> keyframe.renderEditKeyframe(edit -> {}), false)));
        return captured;
    }

    private static Value member(Map<String, Object> captured, String namespace, String member, String owner) {
        Map<String, String> aliases = WidgetKeys.aliases(captured.keySet(), namespace);
        String key = aliases.get(member);
        if (key == null) throw new ExprError(owner + " has no ." + member + " (it has: " + list(aliases.keySet()) + ")");
        Value value = ExpressionRuntime.own(captured.get(key), ExpressionBinding.ALL);
        if (value == null) throw new ExprError("." + member + " can't be read");
        return value;
    }

    /** local(p) turns a world position into the shape's own space (where its points live), world(p) the other way. */
    private static Value transform(@Nullable Keyframe keyframe, String owner, String name, Value[] args) {
        boolean toLocal = name.equals("local");
        if (!toLocal && !name.equals("world")) throw new ExprError(owner + " has no " + name + "() (it has: local, world)");
        if (args.length != 1) throw new ExprError(name + "() takes a position");
        if (!(keyframe instanceof ShapeKeyframe shape)) throw new ExprError(name + "() works on shape tracks only");
        Matrix4f matrix = ShapeTrackRegistry.worldTransform(shape.value);
        if (toLocal) {
            if (Math.abs(matrix.determinant()) < 1.0e-12) throw new ExprError("the shape is scaled to zero");
            matrix.invert();
        }
        double[] point = args[0].components(3);
        Vector3f result = matrix.transformPosition(new Vector3f((float) point[0], (float) point[1], (float) point[2]));
        return Value.vec(result.x, result.y, result.z);
    }

    private record TrackRef(KeyframeTrack track, String name, float tick) implements Value.Obj {
        @Override
        public Value member(String member) {
            return References.member(capture(track, name, tick).values(), namespace(track.keyframeType), member, describe());
        }

        @Override
        public Value call(String method, Value[] args) {
            return transform(capture(track, name, tick).keyframe(), describe(), method, args);
        }

        @Override
        public java.util.List<String> members() {
            java.util.List<String> names = new java.util.ArrayList<>(
                    WidgetKeys.aliases(capture(track, name, tick).values().keySet(), namespace(track.keyframeType)).keySet());
            names.add("local()");
            names.add("world()");
            return names;
        }

        @Override
        public String describe() {
            return "track \"" + name + "\"";
        }
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
            if (captured == null) captured = read(keyframe);
            return References.member(captured, namespace, member, "self");
        }

        @Override
        public Value call(String method, Value[] args) {
            return transform(keyframe, "self", method, args);
        }

        @Override
        public java.util.List<String> members() {
            if (captured == null) captured = read(keyframe);
            java.util.List<String> names = new java.util.ArrayList<>(WidgetKeys.aliases(captured.keySet(), namespace).keySet());
            names.add("local()");
            names.add("world()");
            return names;
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
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (uuid != null ? uuid.equals(entity.getUUID()) : id.equalsIgnoreCase(entity.getName().getString())) {
                return new EntityRef(entity);
            }
        }
        throw new ExprError("no entity \"" + id + "\"");
    }

    private static final String ENTITY_MEMBERS = "x, y, z, position, eye, look, velocity, motion, speed, yaw, pitch, "
            + "head_yaw, body_yaw, health, max_health, on_ground, sprinting, sneaking, alive, age, width, height, "
            + "type, name, uuid, nbt, field, data";

    private record EntityRef(Entity entity) implements Value.Obj {
        @Override
        public Value member(String name) {
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            return switch (name) {
                case "x" -> Value.of(entity.getPosition(partial).x);
                case "y" -> Value.of(entity.getPosition(partial).y);
                case "z" -> Value.of(entity.getPosition(partial).z);
                case "position" -> vec(entity.getPosition(partial));
                case "eye" -> vec(entity.getEyePosition(partial));
                case "look" -> vec(entity.getViewVector(partial));
                // A replayed entity is moved by position updates, so its own motion is often zero; this is what it did.
                case "velocity" -> vec(entity.position().subtract(entity.xo, entity.yo, entity.zo).scale(20));
                case "speed" -> Value.of(entity.position().subtract(entity.xo, entity.yo, entity.zo).length() * 20);
                case "motion" -> vec(entity.getDeltaMovement());
                case "yaw" -> Value.of(entity.getYRot());
                case "pitch" -> Value.of(entity.getXRot());
                case "head_yaw" -> Value.of(entity.getYHeadRot());
                case "body_yaw" -> Value.of(living(name).yBodyRot);
                case "health" -> Value.of(living(name).getHealth());
                case "max_health" -> Value.of(living(name).getMaxHealth());
                case "on_ground" -> Value.of(entity.onGround());
                case "sprinting" -> Value.of(entity.isSprinting());
                case "sneaking" -> Value.of(entity.isShiftKeyDown());
                case "alive" -> Value.of(entity.isAlive());
                case "age" -> Value.of(entity.tickCount);
                case "width" -> Value.of(entity.getBbWidth());
                case "height" -> Value.of(entity.getBbHeight());
                case "type" -> Value.text(EntityType.getKey(entity.getType()).toString());
                case "name" -> Value.text(entity.getName().getString());
                case "uuid" -> Value.text(entity.getUUID().toString());
                case "nbt" -> new NbtRef(data(entity), "nbt");
                case "field" -> EntityFields.fields(entity);
                case "data" -> EntityFields.data(entity);
                default -> throw new ExprError("an entity has no ." + name + " (it has: " + ENTITY_MEMBERS + ")");
            };
        }

        private LivingEntity living(String member) {
            if (entity instanceof LivingEntity living) return living;
            throw new ExprError("." + member + " is only on living entities");
        }

        @Override
        public java.util.List<String> members() {
            return java.util.List.of(ENTITY_MEMBERS.split(", "));
        }

        @Override
        public String describe() {
            return "entity \"" + entity.getName().getString() + "\"";
        }
    }

    private record SavedData(int age, Vec3 position, CompoundTag tag) {}

    private static final Map<Entity, SavedData> SAVED = new WeakHashMap<>();

    /**
     * Everything the entity saves (what /data get entity shows). Saving is not cheap (a player writes its whole
     * inventory), and it only changes when the entity ticks or is moved by a seek, so it is kept until then.
     */
    private static CompoundTag data(Entity entity) {
        SavedData saved = SAVED.get(entity);
        if (saved != null && saved.age() == entity.tickCount && saved.position().equals(entity.position())) return saved.tag();
        CompoundTag tag;
        try {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
            entity.saveWithoutId(output);
            tag = output.buildResult();
        } catch (RuntimeException error) {
            throw new ExprError("the entity's data can't be read: " + error.getMessage());
        }
        SAVED.put(entity, new SavedData(entity.tickCount, entity.position(), tag));
        return tag;
    }

    private static Value nbt(Tag tag, String path) {
        Optional<CompoundTag> compound = tag.asCompound();
        if (compound.isPresent()) return new NbtRef(compound.get(), path);
        Optional<ListTag> list = tag.asList();
        if (list.isPresent()) {
            ListTag items = list.get();
            double[] numbers = new double[items.size()];
            for (int i = 0; i < items.size(); i++) {
                Optional<Number> number = items.get(i).asNumber();
                if (number.isEmpty()) return new NbtList(items, path);
                numbers[i] = number.get().doubleValue();
            }
            return new Value.Vec(numbers);
        }
        Optional<int[]> ints = tag.asIntArray();
        if (ints.isPresent()) return vector(ints.get().length, i -> ints.get()[i]);
        Optional<long[]> longs = tag.asLongArray();
        if (longs.isPresent()) return vector(longs.get().length, i -> longs.get()[i]);
        Optional<byte[]> bytes = tag.asByteArray();
        if (bytes.isPresent()) return vector(bytes.get().length, i -> bytes.get()[i]);
        Optional<Number> number = tag.asNumber();
        if (number.isPresent()) return Value.of(number.get().doubleValue());
        return Value.text(tag.asString().orElse(tag.toString()));
    }

    private static Value vector(int length, java.util.function.IntToDoubleFunction value) {
        double[] values = new double[length];
        for (int i = 0; i < length; i++) values[i] = value.applyAsDouble(i);
        return new Value.Vec(values);
    }

    private record NbtRef(CompoundTag tag, String path) implements Value.Obj {
        @Override
        public Value member(String name) {
            Tag child = tag.get(name);
            if (child == null) {
                throw new ExprError(path + " has no " + name + " (it has: " + list(tag.keySet()) + ")");
            }
            return nbt(child, path + "." + name);
        }

        @Override
        public java.util.List<String> members() {
            return java.util.List.copyOf(tag.keySet());
        }

        @Override
        public String describe() {
            return path;
        }
    }

    private record NbtList(ListTag list, String path) implements Value.Obj {
        @Override
        public Value member(String name) {
            if (name.equals("length")) return Value.of(list.size());
            throw new ExprError(path + " is a list; use [index] or .length");
        }

        @Override
        public Value index(int index) {
            if (index < 0 || index >= list.size()) throw new ExprError(path + "[" + index + "] is outside 0.." + (list.size() - 1));
            return nbt(list.get(index), path + "[" + index + "]");
        }

        @Override
        public java.util.List<String> members() {
            return java.util.List.of("length");
        }

        @Override
        public String describe() {
            return path;
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
                case "position" -> vec(position);
                case "yaw" -> Value.of(camera.yRot());
                case "pitch" -> Value.of(camera.xRot());
                case "fov" -> Value.of(camera.getFov());
                default -> throw new ExprError("the camera has no ." + name + " (it has: x, y, z, position, yaw, pitch, fov)");
            };
        }

        @Override
        public java.util.List<String> members() {
            return java.util.List.of("x", "y", "z", "position", "yaw", "pitch", "fov");
        }

        @Override
        public String describe() {
            return "the camera";
        }
    }

    private static Value vec(Vec3 vector) {
        return Value.vec(vector.x, vector.y, vector.z);
    }

    private static String list(Iterable<String> names) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String name : names) {
            if (count++ == 24) return builder.append(", ...").toString();
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(name);
        }
        return builder.toString();
    }
}
