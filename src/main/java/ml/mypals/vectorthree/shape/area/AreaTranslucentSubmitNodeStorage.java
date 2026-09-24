package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;

import java.util.List;

final class AreaTranslucentSubmitNodeStorage extends SubmitNodeStorage {
    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
            int light, int overlay, int color, UvMapping uvMapping, int outlineColor) {
        super.submitModel(model, state, poseStack, AreaBlockEntityTranslucency.entityVariantOf(renderType),
                light, overlay, color, uvMapping, outlineColor);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
            int[] tints, int light, int overlay, int outlineColor) {
        super.submitBlockModel(poseStack, AreaBlockEntityTranslucency.blockVariantOf(renderType), parts,
                tints, light, overlay, outlineColor);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer) {
        super.submitCustomGeometry(poseStack, AreaBlockEntityTranslucency.entityVariantOf(renderType), renderer);
    }
}
