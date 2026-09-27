package ml.mypals.vectorthree.shape.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.EntityShape;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.flashback.pose.EntityPoses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public final class ProjectedEntityShape extends EntityShape {
    private final @Nullable UUID sourceId;
    private final UUID identity;
    private @Nullable Entity source;

    public ProjectedEntityShape(String shapeId, @Nullable UUID sourceId, Entity placeholder) {
        super(transformer -> {}, Vec3.ZERO, placeholder, 0xF000F0);
        this.sourceId = sourceId;
        this.identity = ShapeEntities.uuidOf(shapeId);
    }

    public @Nullable Entity source() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity found = sourceId == null || minecraft.level == null ? null : minecraft.level.getEntity(sourceId);
        if (found != null && found != source) {
            source = found;
            entity = found;
            generateRawGeometry(true);
        }
        return found;
    }

    @Override
    protected void drawInternal(VertexBuilder builder) {
        Entity projected = source();
        if (projected == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderState state = EntityPoses.asIdentity(identity,
                () -> dispatcher.extractEntity(projected, transformer.getTickDelta()));
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(builder.getPositionMatrix());
        SubmitNodeStorage storage = new SubmitNodeStorage();
        dispatcher.submit(state, minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState,
                0, 0, 0, poseStack, storage);
        Helpers.renderFeatures(minecraft, storage);
    }
}
