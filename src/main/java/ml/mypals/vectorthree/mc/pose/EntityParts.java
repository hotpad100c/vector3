package ml.mypals.vectorthree.mc.pose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ml.mypals.vectorthree.mixin.minecraft.pose.LivingEntityRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
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
 * Where an entity's model part is right now, for mounting shapes and the camera on it. The model is set up the way its
 * renderer's submit does it (for a LivingEntityRenderer: body rotation, scale, the flip into model space, the 1.501
 * drop; the ender dragon has its own; any other renderer starts from the model matrix it last drew the entity with),
 * posed by the entity's animation and pose keyframes, and the part's transform is
 * taken along its parent chain. Render thread only (the model is shared and set up in place, as every use of it does
 * anyway).
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
    public static @Nullable Matrix4f transform(Entity entity, String part, float partialTick) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        PoseStack stack = new PoseStack();
        Model model;
        Object state;
        if (renderer instanceof LivingEntityRenderer living && entity instanceof LivingEntity) {
            model = living.getModel();
            LivingEntityRenderState livingState = (LivingEntityRenderState) living.createRenderState(entity, partialTick);
            state = livingState;
            stack.scale(livingState.scale, livingState.scale, livingState.scale);
            ((LivingEntityRendererInvoker) living).vector3$setupRotations(livingState, stack, livingState.bodyRot,
                    livingState.scale);
            stack.scale(-1, -1, 1);
            ((LivingEntityRendererInvoker) living).vector3$scale(livingState, stack);
            stack.translate(0, -1.501f, 0);
        } else if (renderer instanceof EnderDragonRenderer dragon) {
            model = ((EntityPoses.ModelOwner) dragon).vector3$poseModel();
            EnderDragonRenderState dragonState = (EnderDragonRenderState) renderer.createRenderState(entity, partialTick);
            state = dragonState;
            // As EnderDragonRenderer#submit: heading and pitch from its flight history, then into model space.
            float yRot = dragonState.getHistoricalPos(7).yRot();
            float pitch = (float) (dragonState.getHistoricalPos(5).y() - dragonState.getHistoricalPos(10).y());
            stack.mulPose(Axis.YP.rotationDegrees(-yRot));
            stack.mulPose(Axis.XP.rotationDegrees(pitch * 10));
            stack.translate(0, 0, 1);
            stack.scale(-1, -1, 1);
            stack.translate(0, -1.501f, 0);
        } else {
            // Any other renderer: its model and model matrix as the entity was last drawn (see EntityPoses).
            Model<?> seen = EntityPoses.seenModel(renderer);
            Matrix4f matrix = EntityPoses.seenMatrix(entity.getUUID());
            if (seen == null || matrix == null) return null;
            model = seen;
            state = renderer.createRenderState(entity, partialTick);
            stack.last().pose().set(matrix);
        }
        EntityPoses.Tree tree = EntityPoses.parts(model);
        ModelPart target = tree.byName().get(part);
        if (target == null) return null;
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
