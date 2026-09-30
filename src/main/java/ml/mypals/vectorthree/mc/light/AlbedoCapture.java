package ml.mypals.vectorthree.mc.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
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
import net.minecraft.client.renderer.texture.UvMapping;
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
            RenderType original, int color, UvMapping uvMapping) {
        if (!collecting || copying || submits == null) return;
        RenderType albedo = TYPES.computeIfAbsent(original, AlbedoCapture::albedoType);
        if (albedo == null) return;
        copying = true;
        try {
            submits.submitModel((Model) model, state, pose, albedo, 0xF000F0, 0, color, uvMapping, 0);
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
            RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> "vector3_geometry_albedo")
                    .withColorAttachment(target.getColorTextureView(), Optional.empty())
                    .withDepthAttachment(target.getDepthTextureView(), OptionalDouble.empty())
                    .build();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                RenderSystem.bindDefaultUniforms(pass);
                if (blocks) BlockAlbedoCapture.draw(pass);
                if (prepared != null) prepared.executeSolid(pass);
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
                GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        else if (target.width != main.width || target.height != main.height) target.resize(main.width, main.height);
    }

    private static RenderType albedoType(RenderType original) {
        Map<String, ?> textures = ((RenderSetupAccessor) (Object) ((RenderTypeAccessor) original).vector3$state())
                .vector3$textures();
        Object binding = textures.get("Sampler0");
        if (binding == null) return null;
        Identifier texture = ((TextureBindingAccessor) binding).vector3$location();
        if (texture == null || !original.format().equals(RenderPipelines.ENTITY_CUTOUT.getVertexFormatBindings().getFirst()))
            return null;
        RenderSetup setup = RenderSetup.builder(pipeline()).withTexture("Sampler0", texture)
                .useLightmap().useOverlay().createRenderSetup();
        return RenderType.create("vector3_albedo_" + texture, setup);
    }

    private static RenderPipeline pipeline() {
        if (pipeline != null) return pipeline;
        RenderPipeline base = RenderPipelines.ENTITY_CUTOUT;
        RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(base.getShaders(), Optional.of(base.getShaderDefines()),
                Optional.of(base.getBindGroupLayouts()), base.getColorTargetStates().toArray(new ColorTargetState[0]),
                base.getColorTargetStates().size(), Optional.ofNullable(base.getDepthStencilState()),
                Optional.of(base.getPolygonMode()), Optional.of(base.isCull()),
                base.getVertexFormatBindings().toArray(new VertexFormat[0]), Optional.of(base.getPrimitiveTopology()),
                base.pushConstantSize());
        pipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Mod.id("pipeline/entity_albedo"))
                .withVertexShader(Mod.id("core/entity_albedo"))
                .withFragmentShader(Mod.id("core/entity_albedo"))
                .build());
        return pipeline;
    }
}
