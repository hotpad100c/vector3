package ml.mypals.vectorthree.shape.area;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ml.mypals.vectorthree.mixin.area.RenderSetupAccessor;
import ml.mypals.vectorthree.mixin.area.RenderTypeAccessor;
import ml.mypals.vectorthree.mixin.area.TextureBindingAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AreaTintedCollector implements SubmitNodeCollector {
    private static final Map<RenderType, RenderType> ENTITY_VARIANTS = new ConcurrentHashMap<>();
    private static final Map<RenderType, RenderType> GEOMETRY_VARIANTS = new ConcurrentHashMap<>();
    private static final Direction[] DIRECTIONS = Direction.values();

    private final SubmitNodeCollector root;
    private final OrderedSubmitNodeCollector target;
    private final int tint;

    public AreaTintedCollector(SubmitNodeCollector root, float alpha) {
        this(root, root, ARGB.white(alpha));
    }

    private AreaTintedCollector(SubmitNodeCollector root, OrderedSubmitNodeCollector target, int tint) {
        this.root = root;
        this.target = target;
        this.tint = tint;
    }

    private static RenderType entityVariant(RenderType original) {
        if (original.hasBlending()) return original;
        return ENTITY_VARIANTS.computeIfAbsent(original, key -> {
            Identifier texture = textureOf(key);
            return texture == null ? key : RenderTypes.entityTranslucent(texture);
        });
    }

    private static RenderType geometryVariant(RenderType original) {
        if (original.hasBlending()) return original;
        return GEOMETRY_VARIANTS.computeIfAbsent(original, key -> {
            Identifier texture = textureOf(key);
            if (texture != null) {
                RenderType entity = RenderTypes.entityTranslucent(texture);
                if (entity.format().equals(key.format())) return entity;
            }
            RenderType movingBlock = RenderTypes.translucentMovingBlock();
            return movingBlock.format().equals(key.format()) ? movingBlock : key;
        });
    }

    private static Identifier textureOf(RenderType renderType) {
        Map<String, ?> textures = ((RenderSetupAccessor) (Object) ((RenderTypeAccessor) renderType).vector3$state())
                .vector3$textures();
        Object binding = textures.get("Sampler0");
        if (binding == null && !textures.isEmpty()) binding = textures.values().iterator().next();
        return binding == null ? null : ((TextureBindingAccessor) binding).vector3$location();
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return new AreaTintedCollector(root, root.order(order), tint);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
            int light, int overlay, int color, UvMapping uvMapping, int outlineColor) {
        target.submitModel(model, state, poseStack, entityVariant(renderType), light, overlay,
                ARGB.multiply(color, tint), uvMapping, outlineColor);
    }

    @Override
    public void submitBlockModel(@NonNull PoseStack poseStack, @NonNull RenderType renderType, List<BlockStateModelPart> parts,
                                 int @NonNull [] tints, int light, int overlay, int outlineColor) {
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction direction : DIRECTIONS) quads.addAll(part.getQuads(direction));
            quads.addAll(part.getQuads(null));
        }
        submitQuads(poseStack, RenderTypes.translucentMovingBlock(), quads, tints, light, overlay);
    }

    @Override
    public void submitItem(@NonNull PoseStack poseStack, @NonNull ItemDisplayContext displayContext, int light, int overlay,
                           int outlineColor, int @NonNull [] tints, ItemQuads quads, ItemStackRenderState.@NonNull FoilType foilType) {
        Map<RenderType, List<BakedQuad>> byType = new LinkedHashMap<>();
        for (BakedQuad quad : quads.all()) {
            byType.computeIfAbsent(entityVariant(quad.materialInfo().itemRenderType()), key -> new ArrayList<>()).add(quad);
        }
        byType.forEach((renderType, typed) -> submitQuads(poseStack, renderType, typed, tints, light, overlay));
    }

    private void submitQuads(PoseStack poseStack, RenderType renderType, List<BakedQuad> quads, int[] tints,
            int light, int overlay) {
        if (quads.isEmpty()) return;
        target.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(light);
            instance.setOverlayCoords(overlay);
            for (BakedQuad quad : quads) {
                int index = quad.materialInfo().tintIndex();
                int layer = index >= 0 && index < tints.length ? tints[index] : -1;
                instance.setColor(ARGB.multiply(layer, tint));
                consumer.putBakedQuad(pose, quad, instance);
            }
        });
    }

    @Override
    public void submitCustomGeometry(@NonNull PoseStack poseStack, @NonNull RenderType renderType, @NonNull CustomGeometryRenderer renderer) {
        target.submitCustomGeometry(poseStack, geometryVariant(renderType),
                (pose, consumer) -> renderer.render(pose, new Consumer(consumer, tint)));
    }

    @Override
    public void submitText(@NonNull PoseStack poseStack, float x, float y, @NonNull FormattedCharSequence text, boolean dropShadow,
                           Font.@NonNull DisplayMode displayMode, int light, int color, int backgroundColor, int outlineColor) {
        target.submitText(poseStack, x, y, text, dropShadow, displayMode, light, ARGB.multiply(color, tint),
                ARGB.multiply(backgroundColor, tint), outlineColor);
    }

    @Override
    public void submitTextBackground(@NonNull PoseStack poseStack, float x0, float y0, float x1, float y1, int color,
                                     Font.@NonNull DisplayMode displayMode, int light) {
        target.submitTextBackground(poseStack, x0, y0, x1, y1, ARGB.multiply(color, tint), displayMode, light);
    }

    @Override
    public void submitShadow(@NonNull PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        target.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(@NonNull PoseStack poseStack, Vec3 offset, int y, @NonNull Component name, boolean seeThrough, int light,
                              @NonNull CameraRenderState camera) {
        target.submitNameTag(poseStack, offset, y, name, seeThrough, light, camera);
    }

    @Override
    public void submitFlame(@NonNull PoseStack poseStack, @NonNull EntityRenderState state, @NonNull Quaternionf rotation) {
        target.submitFlame(poseStack, state, rotation);
    }

    @Override
    public void submitLeash(@NonNull PoseStack poseStack, EntityRenderState.@NonNull LeashState leash) {
        target.submitLeash(poseStack, leash);
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
            int light, int overlay, int color, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        target.submitCrumblingOverlay(model, state, poseStack, renderType, light, overlay, color, crumbling);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
        target.submitMovingBlock(poseStack, state, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean flag) {
        target.submitBreakingBlockModel(poseStack, parts, progress, flag);
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color,
            float width, boolean flag) {
        target.submitShapeOutline(poseStack, shape, renderType, color, width, flag);
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState state) {
        target.submitQuadParticleGroup(state);
    }

    @Override
    public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean flag) {
        target.submitGizmoPrimitives(group, camera, flag);
    }

    private record Consumer(VertexConsumer delegate, int tint) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                float nx, float ny, float nz) {
            delegate.addVertex(x, y, z, ARGB.multiply(color, tint), u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(ARGB.multiply(ARGB.color(a, r, g, b), tint));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(ARGB.multiply(color, tint));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            delegate.setUv3(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
    }
}
