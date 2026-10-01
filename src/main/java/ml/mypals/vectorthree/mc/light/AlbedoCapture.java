package ml.mypals.vectorthree.mc.light;

import ml.mypals.vectorthree.mc.render.Pipelines;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import ml.mypals.vectorthree.core.Mod;
import ml.mypals.vectorthree.mc.compat.IrisCompat;
import ml.mypals.vectorthree.mixin.minecraft.area.RenderSetupAccessor;
import ml.mypals.vectorthree.mixin.minecraft.area.RenderTypeAccessor;
import ml.mypals.vectorthree.mixin.minecraft.area.TextureBindingAccessor;
import ml.mypals.vectorthree.mixin.minecraft.light.LevelRendererAlbedoAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/** Captures base texture and tint without lightmap, fog or shader-pack lighting. */
public final class AlbedoCapture {
    private static final Map<RenderType, RenderType> TYPES = new ConcurrentHashMap<>();
    private static RenderPipeline pipeline;
    private static RenderTarget target;
    private static SubmitNodeStorage submits;
    private static final Matrix4f modelView = new Matrix4f();
    private static boolean collecting, copying, ready;
    private static int modelCount;

    private AlbedoCapture() {}

    public static void begin() {
        collecting = LightRenderer.hasLights() && !IrisCompat.isRenderingShadowPass();
        if (!collecting) return;
        submits = new SubmitNodeStorage();
        modelView.set(RenderSystem.getModelViewMatrixCopy());
        modelCount = 0;
        ready = false;
    }

    public static void end() {
        collecting = false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void model(Model<?> model, Object state, PoseStack pose,
            RenderType original, int color, TextureAtlasSprite sprite) {
        if (!collecting || copying || submits == null) return;
        RenderType albedo = TYPES.computeIfAbsent(original, AlbedoCapture::albedoType);
        if (albedo == null) return;
        copying = true;
        try {
            submits.submitModel((Model) model, state, pose, albedo, 0xF000F0, 0, color, sprite, 0, null);
            modelCount++;
        } finally {
            copying = false;
        }
    }

    public static void render(RenderTarget main) {
        collecting = false;
        ready = false;
        SubmitNodeStorage frame = submits;
        submits = null;
        if (main.width <= 0 || main.height <= 0) return;
        boolean blocks = BlockAlbedoCapture.prepare();
        if (!blocks && modelCount == 0) return;
        ensureTarget(main);
        FeatureRenderDispatcher dispatcher = ((LevelRendererAlbedoAccessor) Minecraft.getInstance().levelRenderer)
                .vector3$featureRenderDispatcher();
        boolean oldBypass = IrisCompat.bypass();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(modelView);
        IrisCompat.setBypass(true);
        FeatureRenderDispatcher.PreparedFrame prepared = null;
        try {
            if (modelCount > 0) prepared = dispatcher.prepareFrame(frame);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), new Vector4f(0), target.getDepthTexture(), 0.0);
            RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "vector3_geometry_albedo")
                    .withColorAttachment(target.getColorTextureView(), Optional.empty())
                    .withDepthAttachment(target.getDepthTextureView(), OptionalDouble.empty())
                    .withRenderArea(new RenderPass.RenderArea(0, 0, target.width, target.height));
            if (blocks) {
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                    RenderSystem.bindDefaultUniforms(pass);
                    BlockAlbedoCapture.draw(pass);
                }
            }
            if (prepared != null) {
                // 26.2's feature renderers open a pass per draw; the output override points them at the capture.
                GpuTextureView oldColor = RenderSystem.outputColorTextureOverride;
                GpuTextureView oldDepth = RenderSystem.outputDepthTextureOverride;
                RenderSystem.outputColorTextureOverride = target.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
                try {
                    prepared.executeSolid();
                } finally {
                    RenderSystem.outputColorTextureOverride = oldColor;
                    RenderSystem.outputDepthTextureOverride = oldDepth;
                }
            }
            ready = true;
        } finally {
            try {
                if (prepared != null) prepared.close();
            } finally {
                IrisCompat.setBypass(oldBypass);
                stack.popMatrix();
            }
        }
    }

    public static boolean ready() { return ready; }
    public static GpuTextureView colorView() { return ready ? target.getColorTextureView() : null; }
    public static GpuTextureView depthView() { return ready ? target.getDepthTextureView() : null; }
    public static void blockChanged(BlockPos pos) { BlockAlbedoCapture.invalidate(pos); }
    public static void chunkChanged(int x, int z) { BlockAlbedoCapture.invalidateChunk(x, z); }

    public static void clear() {
        collecting = false;
        copying = false;
        ready = false;
        submits = null;
        modelCount = 0;
        BlockAlbedoCapture.clear();
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    private static void ensureTarget(RenderTarget main) {
        if (target == null) target = new TextureTarget("vector3_geometry_albedo", main.width, main.height,
                true, GpuFormat.RGBA8_UNORM);
        else if (target.width != main.width || target.height != main.height) target.resize(main.width, main.height);
    }

    private static RenderType albedoType(RenderType original) {
        Map<String, ?> textures = ((RenderSetupAccessor) (Object) ((RenderTypeAccessor) original).vector3$state())
                .vector3$textures();
        Object binding = textures.get("Sampler0");
        if (binding == null) return null;
        Identifier texture = ((TextureBindingAccessor) binding).vector3$location();
        if (texture == null || !original.format().equals(RenderPipelines.ENTITY_CUTOUT.getVertexFormatBinding(0)))
            return null;
        RenderSetup setup = RenderSetup.builder(pipeline()).withTexture("Sampler0", texture)
                .useLightmap().useOverlay().createRenderSetup();
        return RenderType.create("vector3_albedo_" + texture, setup);
    }

    private static RenderPipeline pipeline() {
        if (pipeline != null) return pipeline;
        RenderPipeline base = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Snippet snippet = Pipelines.snippetOf(base);
        pipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Mod.id("pipeline/entity_albedo"))
                .withVertexShader(Mod.id("core/entity_albedo"))
                .withFragmentShader(Mod.id("core/entity_albedo"))
                .build());
        return pipeline;
    }
}
