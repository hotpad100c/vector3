package ml.mypals.vectorthree.expression;

import ml.mypals.vectorthree.expression.lang.ExprError;
import ml.mypals.vectorthree.expression.lang.Value;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads an entity's own fields (entity.field) and its synched data (entity.data) by name.
 * <p>
 * This is the one place vector3 uses reflection: which fields exist depends on the entity's class (including other
 * mods' entities), so they can't be listed with accessors. It is read-only and limited to what can't leak anything:
 * value-like fields (numbers, booleans, text, enums, vectors, UUIDs, block positions) of the entity's own class chain,
 * and small state objects from the same packages (e.g. walkAnimation), never other entities, the level or the client.
 */
final class EntityFields {
    private static final int MAX_DEPTH = 3;
    private static final MethodType GETTER = MethodType.methodType(Object.class, Object.class);

    private static final ClassValue<Map<String, MethodHandle>> FIELDS = new ClassValue<>() {
        @Override
        protected Map<String, MethodHandle> computeValue(Class<?> type) {
            return scanFields(type);
        }
    };

    private static final ClassValue<Map<String, EntityDataAccessor<?>>> DATA = new ClassValue<>() {
        @Override
        protected Map<String, EntityDataAccessor<?>> computeValue(Class<?> type) {
            return scanData(type);
        }
    };

    private EntityFields() {}

    static Value fields(Entity entity) {
        return new FieldsRef(entity, entity.getClass().getPackageName(), "field", 0);
    }

    static Value data(Entity entity) {
        return new DataRef(entity);
    }

    private static Map<String, MethodHandle> scanFields(Class<?> type) {
        Map<String, MethodHandle> fields = new LinkedHashMap<>();
        // An entity's chain stops at Entity; a state object's is its own classes up to Object.
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || fields.containsKey(field.getName())) continue;
                if (!leaf(field.getType()) && !enterable(field.getType(), type)) continue;
                try {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(current, MethodHandles.lookup());
                    fields.put(field.getName(), lookup.unreflectGetter(field).asType(GETTER));
                } catch (IllegalAccessException ignored) {
                }
            }
            if (current == Entity.class) break;
        }
        return Collections.unmodifiableMap(fields);
    }

    private static Map<String, EntityDataAccessor<?>> scanData(Class<?> type) {
        Map<String, EntityDataAccessor<?>> data = new LinkedHashMap<>();
        for (Class<?> current = type; current != null && Entity.class.isAssignableFrom(current); current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityDataAccessor.class) continue;
                try {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(current, MethodHandles.lookup());
                    EntityDataAccessor<?> accessor = (EntityDataAccessor<?>) lookup.unreflectGetter(field).invoke();
                    data.putIfAbsent(dataName(field.getName()), accessor);
                } catch (Throwable ignored) {
                }
            }
        }
        return Collections.unmodifiableMap(data);
    }

    /** DATA_HEALTH_ID -> health. */
    private static String dataName(String field) {
        String name = field;
        if (name.startsWith("DATA_")) name = name.substring(5);
        if (name.endsWith("_ID")) name = name.substring(0, name.length() - 3);
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean leaf(Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class
                || type == String.class || type.isEnum() || type == Vec3.class || type == Vec2.class || type == UUID.class
                || type == BlockPos.class || Vector3fc.class.isAssignableFrom(type) || Quaternionfc.class.isAssignableFrom(type);
    }

    // Only small state objects that belong with entities: from net.minecraft.world.entity or the entity's own package.
    private static boolean enterable(Class<?> type, Class<?> owner) {
        if (type.isPrimitive() || type.isArray() || type.isInterface() || Entity.class.isAssignableFrom(type)
                || Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return false;
        }
        String pkg = type.getPackageName();
        return pkg.startsWith("net.minecraft.world.entity") || pkg.equals(owner.getPackageName());
    }

    private static Value convert(@Nullable Object value, String path, String rootPackage, int depth) {
        return switch (value) {
            case null -> Value.text("");
            case Boolean bool -> Value.of(bool);
            case Number number -> Value.of(number.doubleValue());
            case Character character -> Value.text(String.valueOf(character));
            case String text -> Value.text(text);
            case Enum<?> constant -> Value.text(constant.name().toLowerCase(Locale.ROOT));
            case Vec3 vec -> Value.vec(vec.x, vec.y, vec.z);
            case Vec2 vec -> Value.vec(vec.x, vec.y);
            case Vector3fc vec -> Value.vec(vec.x(), vec.y(), vec.z());
            case Quaternionfc quaternion -> Value.vec(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
            case Rotations rotations -> Value.vec(rotations.x(), rotations.y(), rotations.z());
            case BlockPos pos -> Value.vec(pos.getX(), pos.getY(), pos.getZ());
            case UUID uuid -> Value.text(uuid.toString());
            case Component component -> Value.text(component.getString());
            case ItemStack stack -> Value.text(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            case Optional<?> optional -> convert(optional.orElse(null), path, rootPackage, depth);
            default -> {
                if (depth < MAX_DEPTH && enterable(value.getClass(), rootPackage)) {
                    yield new FieldsRef(value, rootPackage, path, depth + 1);
                }
                yield Value.text(value.toString());
            }
        };
    }

    private static boolean enterable(Class<?> type, String rootPackage) {
        String pkg = type.getPackageName();
        return !Entity.class.isAssignableFrom(type) && (pkg.startsWith("net.minecraft.world.entity") || pkg.equals(rootPackage));
    }

    private record FieldsRef(Object target, String rootPackage, String path, int depth) implements Value.Obj {
        @Override
        public Value member(String name) {
            MethodHandle getter = FIELDS.get(target.getClass()).get(name);
            if (getter == null) {
                throw new ExprError(path + " has no " + name + " (it has: " + list(FIELDS.get(target.getClass()).keySet()) + ")");
            }
            try {
                return convert(getter.invokeExact(target), path + "." + name, rootPackage, depth);
            } catch (ExprError error) {
                throw error;
            } catch (Throwable error) {
                throw new ExprError(path + "." + name + " can't be read: " + error.getMessage());
            }
        }

        @Override
        public java.util.List<String> members() {
            return java.util.List.copyOf(FIELDS.get(target.getClass()).keySet());
        }

        @Override
        public String describe() {
            return path;
        }
    }

    private record DataRef(Entity entity) implements Value.Obj {
        @Override
        public Value member(String name) {
            Map<String, EntityDataAccessor<?>> data = DATA.get(entity.getClass());
            EntityDataAccessor<?> accessor = data.get(name.toLowerCase(Locale.ROOT));
            if (accessor == null) accessor = data.get(dataName(name));
            if (accessor == null) throw new ExprError("data has no " + name + " (it has: " + list(data.keySet()) + ")");
            try {
                return convert(entity.getEntityData().get(accessor), "data." + name, "", MAX_DEPTH);
            } catch (RuntimeException error) {
                throw new ExprError("data." + name + " can't be read: " + error.getMessage());
            }
        }

        @Override
        public java.util.List<String> members() {
            return java.util.List.copyOf(DATA.get(entity.getClass()).keySet());
        }

        @Override
        public String describe() {
            return "data";
        }
    }

    private static String list(Iterable<String> names) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String name : names) {
            if (count++ == 40) return builder.append(", ...").toString();
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(name);
        }
        return builder.toString();
    }
}
