package ml.mypals.vectorthree.flashback.pose;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.vectorthree.mixin.pose.LivingEntityRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a living entity's model part is right now, for mounting shapes and the camera on it. The model is set up the
 * way LivingEntityRenderer#submit does it (body rotation, scale, the flip into model space, the 1.501 drop), posed by
 * the entity's animation and pose keyframes, and the part's transform is taken along its parent chain. Render thread
 * only (the model is shared and set up in place, as every use of it does anyway).
 */
public final class EntityParts {
    private EntityParts() {}

    /** The part names of the entity's model, root first, or empty when it has no posable model. */
    public static List<String> names(@Nullable Entity entity) {
        return new ArrayList<>(EntityPoses.restPose(entity).keySet());
    }

    /**
     * The part's frame in the world: its pivot, and its orientation with model space's upside-down flip taken out (an
     * unrotated part faces the way the entity does), without scale. Null if the entity has no such part.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Matrix4f transform(LivingEntity entity, String part, float partialTick) {
        EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer living)) return null;
        EntityModel model = living.getModel();
        EntityPoses.Tree tree = EntityPoses.parts(model);
        ModelPart target = tree.byName().get(part);
        if (target == null) return null;

        LivingEntityRenderState state = (LivingEntityRenderState) living.createRenderState(entity, partialTick);
        PoseStack stack = new PoseStack();
        stack.scale(state.scale, state.scale, state.scale);
        ((LivingEntityRendererInvoker) living).vector3$setupRotations(state, stack, state.bodyRot, state.scale);
        stack.scale(-1, -1, 1);
        ((LivingEntityRendererInvoker) living).vector3$scale(state, stack);
        stack.translate(0, -1.501f, 0);
        model.setupAnim(state);
        EntityPoses.afterSetupAnim(model, state, false);

        List<ModelPart> chain = new ArrayList<>();
        for (ModelPart at = target; at != null; at = tree.parents().get(at)) chain.addFirst(at);
        for (ModelPart at : chain) at.translateAndRotate(stack);
        stack.scale(-1, -1, 1);

        Matrix4f local = stack.last().pose();
        Vector3f pivot = local.transformPosition(new Vector3f());
        Quaternionf rotation = local.getNormalizedRotation(new Quaternionf());
        Vec3 at = entity.getPosition(partialTick);
        return new Matrix4f().translation((float) (at.x + pivot.x), (float) (at.y + pivot.y), (float) (at.z + pivot.z))
                .rotate(rotation);
    }
}
