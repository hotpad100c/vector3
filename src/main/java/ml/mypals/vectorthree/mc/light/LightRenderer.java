package ml.mypals.vectorthree.mc.light;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import ml.mypals.vectorthree.core.light.Light;
import ml.mypals.vectorthree.core.fade.effects.GodRaysSettings;
import ml.mypals.vectorthree.mc.vfx.effects.DepthOfFieldEffect;
import ml.mypals.vectorthree.mc.vfx.effects.ScreenPass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LightRenderer {
    private static final List<Light> PENDING = new ArrayList<>();
    private static List<Light> current = List.of();
    private static Light previewOriginal, previewReplacement;
    private static RenderPipeline surfacePipeline, surfaceCompositePipeline, shadowPipeline,
            volumePipeline, volumeCompositePipeline;
    private static GpuBuffer settings;
    private static RenderTarget surfaceTarget, shadowTarget, volumeTarget;

    private LightRenderer() {}

    public static void begin() { PENDING.clear(); }
    public static void request(Light light) { PENDING.add(light); }
    public static void finish() { current = List.copyOf(PENDING); }
    public static void preview(Light original, Light replacement) {
        previewOriginal = original;
        previewReplacement = replacement;
    }
    public static void clearPreview() {
        previewOriginal = null;
        previewReplacement = null;
    }
    private static Light previewed(Light light) {
        return previewOriginal != null && previewOriginal.equals(light) ? previewReplacement : light;
    }

    /** What is drawn for a requested light: any gizmo preview applied, moved into the world, or null when hidden. */
    private static Light shown(Light requested) {
        Light light = previewed(requested);
        return LightParents.hidden(light) ? null : LightParents.toWorld(light);
    }
    public static void clear() {
        clearPreview();
        PENDING.clear();
        current = List.of();
        if (surfaceTarget != null) {
            surfaceTarget.destroyBuffers();
            surfaceTarget = null;
        }
        if (shadowTarget != null) {
            shadowTarget.destroyBuffers();
            shadowTarget = null;
        }
        if (volumeTarget != null) {
            volumeTarget.destroyBuffers();
            volumeTarget = null;
        }
    }
    public static boolean hasLights() { return !current.isEmpty(); }

    static List<Light> activeLights() {
        return current.stream().map(LightRenderer::shown).filter(java.util.Objects::nonNull)
                .filter(light -> light.intensity() > 0 && light.radius() > 0).toList();
    }

    public static void render(RenderTarget main, GodRaysSettings localRays) {
        if (current.isEmpty() || !DepthOfFieldEffect.hasCapturedDepth() || main.width <= 0 || main.height <= 0) return;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        if (!camera.isInitialized()) return;
        ensure();
        ensureSurface(main);
        ensureShadow(main);
        RenderTarget scene = ScreenPass.scratch(main);
        ScreenPass.copy(main, scene);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                surfaceTarget.getColorTexture(), new Vector4f(0));
        boolean hasVolume = current.stream().map(LightRenderer::shown).filter(java.util.Objects::nonNull)
                .anyMatch(light -> light.intensity() > 0 && light.volume() > 0);
        if (hasVolume) {
            ensureVolume(main);
            assert volumeTarget.getColorTexture() != null;
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                    volumeTarget.getColorTexture(), new Vector4f(0));
        }
        Vector3f forward = new Vector3f(camera.forwardVector());
        Vector3f up = new Vector3f(camera.upVector());
        Vector3f right = new Vector3f(forward).cross(up).normalize();
        for (Light requested : current) {
            Light light = shown(requested);
            if (light == null || light.intensity() <= 0 || light.radius() <= 0) continue;
            Vector3f offset = new Vector3f((float) (light.position().x - camera.position().x),
                    (float) (light.position().y - camera.position().y),
                    (float) (light.position().z - camera.position().z));
            Matrix4fc projection = DepthOfFieldEffect.projectionMatrix();
            Matrix4fc inverse = DepthOfFieldEffect.inverseProjectionMatrix();
            Vector3f direction = new Vector3f((float) light.direction().x,
                    (float) light.direction().y, (float) light.direction().z);
            Vector3f areaRight = new Vector3f(direction).cross(0, 1, 0);
            if (areaRight.lengthSquared() < 1e-6f) areaRight.set(1, 0, 0);
            areaRight.normalize();
            Vector3f areaUp = new Vector3f(areaRight).cross(direction).normalize();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var data = Std140Builder.onStack(stack, 256)
                        .putVec4(offset.dot(right), offset.dot(up), -offset.dot(forward), light.radius())
                        .putVec4(light.red(), light.green(), light.blue(), light.intensity())
                        .putVec4(DepthOfFieldEffect.projectionX(), DepthOfFieldEffect.projectionY(),
                                1f / main.width, 1f / main.height)
                        .putVec4(light.volume(), light.shadow(), localRays == null ? 0 : localRays.intensity(),
                                localRays == null ? 0 : localRays.samples())
                        .putVec4(projection.m00(), projection.m01(), projection.m02(), projection.m03())
                        .putVec4(projection.m10(), projection.m11(), projection.m12(), projection.m13())
                        .putVec4(projection.m20(), projection.m21(), projection.m22(), projection.m23())
                        .putVec4(projection.m30(), projection.m31(), projection.m32(), projection.m33())
                        .putVec4(inverse.m00(), inverse.m01(), inverse.m02(), inverse.m03())
                        .putVec4(inverse.m10(), inverse.m11(), inverse.m12(), inverse.m13())
                        .putVec4(inverse.m20(), inverse.m21(), inverse.m22(), inverse.m23())
                        .putVec4(inverse.m30(), inverse.m31(), inverse.m32(), inverse.m33())
                        .putVec4(direction.dot(right), direction.dot(up), -direction.dot(forward),
                                light.type().ordinal())
                        .putVec4(light.areaWidth(), light.areaHeight(),
                                (float) Math.cos(Math.toRadians(light.innerAngle())),
                                (float) Math.cos(Math.toRadians(light.outerAngle())))
                        .putVec4(areaRight.dot(right), areaRight.dot(up), -areaRight.dot(forward), light.areaReach())
                        .putVec4(areaUp.dot(right), areaUp.dot(up), -areaUp.dot(forward),
                                AlbedoCapture.ready()
                                        ? (RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 2 : 1) : 0).get();
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
            }
            if (light.shadow() > 0) {
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "vector3_light_shadow", shadowTarget.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(shadowPipeline));
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
                    pass.setUniform("LightSettings", settings);
                    pass.draw(3, 1, 0, 0);
                }
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "vector3_light_surface", surfaceTarget.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(surfacePipeline));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", scene.getColorTextureView(), ScreenPass.nearest());
                pass.setUniform("AlbedoSampler", AlbedoCapture.ready()
                        ? AlbedoCapture.colorView() : scene.getColorTextureView(), ScreenPass.nearest());
                pass.setUniform("AlbedoDepthSampler", AlbedoCapture.ready()
                        ? AlbedoCapture.depthView() : DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
                pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
                pass.setUniform("ShadowSampler", shadowTarget.getColorTextureView(), ScreenPass.nearest());
                pass.setUniform("LightSettings", settings);
                pass.draw(3, 1, 0, 0);
            }
            if (light.volume() > 0) {
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "vector3_light_volume", volumeTarget.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(volumePipeline));
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
                    pass.setUniform("LightSettings", settings);
                    pass.draw(3, 1, 0, 0);
                }
            }
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_light_surface_composite", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(surfaceCompositePipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", scene.getColorTextureView(), ScreenPass.nearest());
            pass.setUniform("LightSampler", surfaceTarget.getColorTextureView(), ScreenPass.nearest());
            pass.draw(3, 1, 0, 0);
        }
        if (hasVolume) {
            ScreenPass.copy(main, scene);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "vector3_light_volume_composite", main.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(volumeCompositePipeline));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", volumeTarget.getColorTextureView(), ScreenPass.linear());
                pass.setUniform("SceneSampler", scene.getColorTextureView(), ScreenPass.nearest());
                pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
                pass.draw(3, 1, 0, 0);
            }
        }
    }

    private static void ensure() {
        if (surfacePipeline != null) return;
        BindGroupLayout surfaceLayout = BindGroupLayout.builder()
                .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("AlbedoSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("AlbedoDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("ShadowSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("LightSettings", UniformType.UNIFORM_BUFFER).build();
        surfacePipeline = ScreenPass.pipeline("light_surface", surfaceLayout,
                GpuFormat.RGBA16_FLOAT, BlendFunction.ADDITIVE);
        surfaceCompositePipeline = ScreenPass.pipeline("light_surface_composite", BindGroupLayout.builder()
                .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("LightSampler", UniformType.COMBINED_IMAGE_SAMPLER).build(),
                GpuFormat.RGBA8_UNORM, null);
        shadowPipeline = ScreenPass.pipeline("light_shadow", BindGroupLayout.builder()
                .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("LightSettings", UniformType.UNIFORM_BUFFER).build(),
                GpuFormat.R16_FLOAT, null);
        volumePipeline = ScreenPass.pipeline("light_volume", BindGroupLayout.builder()
                .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("LightSettings", UniformType.UNIFORM_BUFFER).build(),
                GpuFormat.RGBA16_FLOAT, BlendFunction.ADDITIVE);
        volumeCompositePipeline = ScreenPass.pipeline("light_volume_composite", BindGroupLayout.builder()
                .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER).build(),
                GpuFormat.RGBA8_UNORM, null);
        settings = RenderSystem.getDevice().createBuffer(() -> "vector3_light_settings",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 256);
    }

    private static void ensureShadow(RenderTarget main) {
        int width = Math.max(1, main.width / 2), height = Math.max(1, main.height / 2);
        if (shadowTarget == null) {
            shadowTarget = new TextureTarget("vector3_light_shadow", width, height, GpuFormat.R16_FLOAT, null);
        } else if (shadowTarget.width != width || shadowTarget.height != height) {
            shadowTarget.resize(width, height);
        }
    }

    private static void ensureSurface(RenderTarget main) {
        if (surfaceTarget == null) {
            surfaceTarget = new TextureTarget("vector3_light_surface", main.width, main.height,
                    GpuFormat.RGBA16_FLOAT, null);
        } else if (surfaceTarget.width != main.width || surfaceTarget.height != main.height) {
            surfaceTarget.resize(main.width, main.height);
        }
    }

    private static void ensureVolume(RenderTarget main) {
        int width = Math.max(1, main.width / 2), height = Math.max(1, main.height / 2);
        if (volumeTarget == null) {
            volumeTarget = new TextureTarget("vector3_light_volume", width, height, GpuFormat.RGBA16_FLOAT, null);
        } else if (volumeTarget.width != width || volumeTarget.height != height) {
            volumeTarget.resize(width, height);
        }
    }
}
