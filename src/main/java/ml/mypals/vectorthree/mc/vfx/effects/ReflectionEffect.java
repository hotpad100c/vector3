package ml.mypals.vectorthree.mc.vfx.effects;

import ml.mypals.vectorthree.core.fade.effects.ReflectionSettings;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class ReflectionEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer settings;
    private ReflectionEffect() {}

    public static void render(RenderTarget main, ReflectionSettings value) {
        if (!DepthOfFieldEffect.hasCapturedDepth() || value.intensity() <= 0.001f) return;
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (!camera.isInitialized()) return;
        GpuTextureView mask = ReflectionMaterialMask.view(minecraft.level,
                BlockPos.containing(camera.position()), value.materials());
        if (mask == null) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("reflection", BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withSampler("DistanceSampler")
                    .withSampler("MaterialSampler")
                    .withUniform("ReflectionSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            settings = RenderSystem.getDevice().createBuffer(() -> "vector3_reflection",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 96);
        }
        Vector3f forward = new Vector3f(camera.forwardVector());
        Vector3f up = new Vector3f(camera.upVector());
        Vector3f right = new Vector3f(forward).cross(up).normalize();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 96)
                    .putVec4(value.intensity(), value.maxDistance(), value.thickness(), value.steps())
                    .putVec4(1f / main.width, 1f / main.height, DepthOfFieldEffect.projectionX(),
                            DepthOfFieldEffect.projectionY())
                    .putVec4((float) (camera.position().x - ReflectionMaterialMask.originX()),
                            (float) (camera.position().y - ReflectionMaterialMask.originY()),
                            (float) (camera.position().z - ReflectionMaterialMask.originZ()), 0)
                    .putVec4(right.x, right.y, right.z, 0)
                    .putVec4(up.x, up.y, up.z, 0)
                    .putVec4(forward.x, forward.y, forward.z, 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        assert target.getColorTextureView() != null;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_reflection", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.bindTexture("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
            pass.bindTexture("MaterialSampler", mask, ScreenPass.nearest());
            pass.setUniform("ReflectionSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }

    public static void clear() { ReflectionMaterialMask.clear(); }
}
