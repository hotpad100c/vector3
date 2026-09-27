package ml.mypals.vectorthree.flashback.pose;

import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import ml.mypals.vectorthree.mixin.pose.ModelPartAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Applies entity pose keyframes while models are set up. Poses are collected from the keyframes on the render
 * thread (begin / request / finish, like LookToCamera), one per track in track order, handed to each
 * LivingEntityRenderState at extraction, and applied in that order after every setupAnim that state goes through (the
 * entity's own model, and each armour / cape / elytra model submitted with it), so everything attached follows.
 */
public final class EntityPoses {
    /** Implemented on LivingEntityRenderState. */
    public interface Holder {
        @Nullable UUID vector3$poseEntity();

        List<EntityPose> vector3$poses();

        void vector3$setPoses(@Nullable UUID entity, List<EntityPose> poses);
    }

    private static final Map<UUID, List<EntityPose>> pending = new HashMap<>();
    private static Map<UUID, List<EntityPose>> current = Map.of();
    private static final Set<UUID> snapshotRequests = new HashSet<>();
    private static final Map<UUID, Map<String, EntityPose.Limb>> snapshots = new HashMap<>();
    private static final Map<Model<?>, Tree> PARTS = new WeakHashMap<>();
    private static final Map<UUID, Preview> previews = new HashMap<>();

    /** {@code replacement} shown in place of the track pose {@code original} (by identity), or on top if absent. */
    private record Preview(EntityPose original, EntityPose replacement) {}

    /** A model's parts by name (first wins), and each part's parent. */
    record Tree(Map<String, ModelPart> byName, Map<ModelPart, ModelPart> parents) {}

    /** Where a part sits this frame: its pivot and the world axes its X / Y / Z rotations turn about. */
    /** {@code moveX..Z}: the world displacement of one unit of offset; {@code x..z}: the part's offset from rest. */
    public record Frame(Vec3 pivot, Vec3 xAxis, Vec3 yAxis, Vec3 zAxis, boolean mirrored, float xRot, float yRot, float zRot,
                        Vec3 moveX, Vec3 moveY, Vec3 moveZ, float x, float y, float z) {}

    private EntityPoses() {}

    public static void begin(KeyframeHandler handler) {
        if (handler instanceof MinecraftKeyframeHandler) pending.clear();
    }

    /** Called once per track, in track order; several tracks may pose one entity. */
    public static void request(EntityPose pose) {
        if (pose.entity() != null) pending.computeIfAbsent(pose.entity(), entity -> new ArrayList<>()).add(pose);
    }

    public static void finish(KeyframeHandler handler) {
        if (!(handler instanceof MinecraftKeyframeHandler)) return;
        Map<UUID, List<EntityPose>> next = new HashMap<>();
        pending.forEach((entity, poses) -> next.put(entity, List.copyOf(poses)));
        current = next;
    }

    public static void clear() {
        previews.clear();
        pending.clear();
        current = Map.of();
        snapshotRequests.clear();
        snapshots.clear();
    }

    private static @Nullable UUID identity;

    /** Extracts under another UUID: a projected entity poses as its shape (see ProjectedEntityShape). */
    public static <T> T asIdentity(UUID uuid, java.util.function.Supplier<T> extract) {
        UUID previous = identity;
        identity = uuid;
        try {
            return extract.get();
        } finally {
            identity = previous;
        }
    }

    /** Whose poses an entity being extracted takes: the projecting shape's when it has any, else its own. */
    public static UUID poseIdentity(UUID own) {
        UUID override = identity;
        return override == null || get(override).isEmpty() && !get(own).isEmpty() ? own : override;
    }

    public static List<EntityPose> get(UUID entity) {
        List<EntityPose> poses = current.isEmpty() ? List.of() : current.getOrDefault(entity, List.of());
        Preview preview = previews.isEmpty() ? null : previews.get(entity);
        if (preview == null) return poses;
        List<EntityPose> shown = new ArrayList<>(poses);
        int index = -1;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i) == preview.original()) index = i;
        }
        if (index >= 0) shown.set(index, preview.replacement());
        else shown.add(preview.replacement());
        return shown;
    }

    /**
     * Shows {@code replacement} instead of the track pose {@code original} until cleared, e.g. while a gizmo drags;
     * the other tracks keep applying around it.
     */
    public static void preview(@Nullable UUID entity, EntityPose original, @Nullable EntityPose replacement) {
        if (entity == null) return;
        if (replacement == null) previews.remove(entity);
        else previews.put(entity, new Preview(original, replacement));
    }

    public static void clearPreview(@Nullable UUID entity) {
        if (entity != null) previews.remove(entity);
    }

    /** Called right after a model's setupAnim with the render state it was given; {@code snapshot} allows taking a requested snapshot first. */
    public static void afterSetupAnim(Model<?> model, Object state, boolean snapshot) {
        if (!(state instanceof Holder holder) || holder.vector3$poseEntity() == null) return;
        UUID entity = holder.vector3$poseEntity();
        if (snapshot && snapshotRequests.remove(entity)) snapshots.put(entity, read(model));
        for (EntityPose pose : holder.vector3$poses()) apply(model, pose);
    }

    private static void apply(Model<?> model, EntityPose pose) {
        Map<String, ModelPart> parts = parts(model).byName();
        boolean absolute = pose.mode() == EntityPose.Mode.ABSOLUTE;
        for (Map.Entry<String, EntityPose.Limb> entry : pose.parts().entrySet()) {
            EntityPose.Limb limb = entry.getValue();
            ModelPart part = parts.get(entry.getKey());
            if (part == null) continue;
            if (limb.rotate()) {
                float xRot = limb.xRot() * Mth.DEG_TO_RAD, yRot = limb.yRot() * Mth.DEG_TO_RAD, zRot = limb.zRot() * Mth.DEG_TO_RAD;
                part.xRot = absolute ? xRot : part.xRot + xRot;
                part.yRot = absolute ? yRot : part.yRot + yRot;
                part.zRot = absolute ? zRot : part.zRot + zRot;
            }
            if (limb.move()) {
                PartPose rest = part.getInitialPose();
                part.x = (absolute ? rest.x() : part.x) + limb.x();
                part.y = (absolute ? rest.y() : part.y) + limb.y();
                part.z = (absolute ? rest.z() : part.z) + limb.z();
            }
        }
    }

    /** The model's pose as absolute limbs: every rotation, and the offsets of parts moved from their rest position. */
    private static Map<String, EntityPose.Limb> read(Model<?> model) {
        Map<String, EntityPose.Limb> limbs = new LinkedHashMap<>();
        parts(model).byName().forEach((name, part) -> {
            PartPose rest = part.getInitialPose();
            float x = part.x - rest.x(), y = part.y - rest.y(), z = part.z - rest.z();
            boolean moved = Math.abs(x) > 1.0e-4f || Math.abs(y) > 1.0e-4f || Math.abs(z) > 1.0e-4f;
            limbs.put(name, new EntityPose.Limb(true, moved, part.xRot * Mth.RAD_TO_DEG, part.yRot * Mth.RAD_TO_DEG,
                    part.zRot * Mth.RAD_TO_DEG, x, y, z));
        });
        return limbs;
    }

    /** Asks for the entity's animated pose on its next frame; poll with {@link #takeSnapshot}. */
    public static void requestSnapshot(UUID entity) {
        snapshotRequests.add(entity);
        snapshots.remove(entity);
    }

    public static @Nullable Map<String, EntityPose.Limb> takeSnapshot(UUID entity) {
        return snapshots.remove(entity);
    }

    public static boolean snapshotPending(UUID entity) {
        return snapshotRequests.contains(entity);
    }

    /** The entity's model parts at rest (disabled), root first, or an empty map when it has no posable model. */
    public static Map<String, EntityPose.Limb> restPose(@Nullable Entity entity) {
        if (entity == null) return Map.of();
        EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)) return Map.of();
        Map<String, EntityPose.Limb> limbs = new LinkedHashMap<>();
        parts(living.getModel()).byName().forEach((name, part) -> {
            PartPose rest = part.getInitialPose();
            limbs.put(name, new EntityPose.Limb(false, false, rest.xRot() * Mth.RAD_TO_DEG, rest.yRot() * Mth.RAD_TO_DEG,
                    rest.zRot() * Mth.RAD_TO_DEG, 0, 0, 0));
        });
        return limbs;
    }

    /**
     * Each part's frame for the model as it is set up now, drawn with {@code entityMatrix} (model space to
     * camera-relative world, as LivingEntityRenderer submits it) from a camera at {@code camera}.
     */
    public static Map<String, Frame> frames(Model<?> model, Matrix4f entityMatrix, Vec3 camera) {
        Tree tree = parts(model);
        Map<String, Frame> frames = new LinkedHashMap<>();
        tree.byName().forEach((name, part) -> {
            List<ModelPart> chain = new ArrayList<>();
            for (ModelPart parent = tree.parents().get(part); parent != null; parent = tree.parents().get(parent)) {
                chain.addFirst(parent);
            }
            PoseStack stack = new PoseStack();
            stack.last().pose().set(entityMatrix);
            for (ModelPart parent : chain) parent.translateAndRotate(stack);
            Matrix4f parentMatrix = stack.last().pose();
            Vector3f pivot = parentMatrix.transformPosition(new Vector3f(part.x / 16f, part.y / 16f, part.z / 16f));
            Quaternionf z = new Quaternionf().rotationZ(part.zRot);
            Quaternionf zy = new Quaternionf(z).rotateY(part.yRot);
            PartPose rest = part.getInitialPose();
            frames.put(name, new Frame(new Vec3(pivot.x + camera.x, pivot.y + camera.y, pivot.z + camera.z),
                    worldAxis(parentMatrix, zy.transform(new Vector3f(1, 0, 0))),
                    worldAxis(parentMatrix, z.transform(new Vector3f(0, 1, 0))),
                    worldAxis(parentMatrix, new Vector3f(0, 0, 1)),
                    parentMatrix.determinant3x3() < 0,
                    part.xRot * Mth.RAD_TO_DEG, part.yRot * Mth.RAD_TO_DEG, part.zRot * Mth.RAD_TO_DEG,
                    worldStep(parentMatrix, 1, 0, 0), worldStep(parentMatrix, 0, 1, 0), worldStep(parentMatrix, 0, 0, 1),
                    part.x - rest.x(), part.y - rest.y(), part.z - rest.z()));
        });
        return frames;
    }

    private static Vec3 worldStep(Matrix4f matrix, float x, float y, float z) {
        Vector3f world = matrix.transformDirection(new Vector3f(x / 16f, y / 16f, z / 16f));
        return new Vec3(world.x, world.y, world.z);
    }

    private static Vec3 worldAxis(Matrix4f matrix, Vector3f local) {
        Vector3f world = matrix.transformDirection(local).normalize();
        return new Vec3(world.x, world.y, world.z);
    }

    // Part names are looked up through the whole tree once per model; the first part with a name wins.
    static Tree parts(Model<?> model) {
        return PARTS.computeIfAbsent(model, m -> {
            Tree tree = new Tree(new LinkedHashMap<>(), new java.util.IdentityHashMap<>());
            tree.byName().put(EntityPose.ROOT, m.root());
            collect(m.root(), tree);
            return tree;
        });
    }

    private static void collect(ModelPart part, Tree tree) {
        ((ModelPartAccessor) (Object) part).vector3$children().forEach((name, child) -> {
            tree.byName().putIfAbsent(name, child);
            tree.parents().put(child, part);
            collect(child, tree);
        });
    }
}
