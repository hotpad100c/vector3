package ml.mypals.vectorthree.mc.fade.effects;

import ml.mypals.vectorthree.core.fade.effects.MotionBlurSettings;

import ml.mypals.vectorthree.mc.camera.PreviewPass;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class MotionBlurEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer settings;
    private static final History[] HISTORY = { new History(), new History() };

    private static final class History {
        Vec3 position;
        Vector3f right, up, forward;
    }

    private MotionBlurEffect() {}
    public static void clear() { for (History history : HISTORY) history.position = null; }

    public static void render(RenderTarget main, MotionBlurSettings value) {
        if (!DepthOfFieldEffect.hasCapturedDepth()) return;
        History history = HISTORY[PreviewPass.isRendering() ? 1 : 0];
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 position = camera.position();
        Vector3f right = new Vector3f(camera.leftVector()).negate();
        Vector3f up = new Vector3f(camera.upVector());
        Vector3f forward = new Vector3f(camera.forwardVector());
        boolean valid = history.position != null && position.distanceTo(history.position) < 8
                && forward.dot(history.forward) > 0.7f;
        Vec3 delta = valid ? position.subtract(history.position) : Vec3.ZERO;
        Vector3f oldRight = valid ? history.right : right;
        Vector3f oldUp = valid ? history.up : up;
        Vector3f oldForward = valid ? history.forward : forward;
        history.position = position;
        history.right = right;
        history.up = up;
        history.forward = forward;
        if (!valid || value.strength() <= 0.001f) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("motion_blur", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("MotionSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            settings = RenderSystem.getDevice().createBuffer(() -> "vector3_motion_blur",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 128);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 128)
                    .putVec4(right.x, right.y, right.z, DepthOfFieldEffect.projectionX())
                    .putVec4(up.x, up.y, up.z, DepthOfFieldEffect.projectionY())
                    .putVec4(forward.x, forward.y, forward.z, value.strength())
                    .putVec4(oldRight.x, oldRight.y, oldRight.z, value.samples())
                    .putVec4(oldUp.x, oldUp.y, oldUp.z, 0)
                    .putVec4(oldForward.x, oldForward.y, oldForward.z, 0)
                    .putVec4((float) delta.x, (float) delta.y, (float) delta.z, 0)
                    .putVec4(main.width, main.height, value.maxPixels(), 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_motion_blur", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
            pass.setUniform("MotionSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
