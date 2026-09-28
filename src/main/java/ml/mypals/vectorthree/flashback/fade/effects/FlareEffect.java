package ml.mypals.vectorthree.flashback.fade.effects;

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
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

public final class FlareEffect {
    private static RenderPipeline pipeline;
    private static GpuBuffer settings;
    private FlareEffect() {}

    public static void render(RenderTarget main, FlareSettings value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (value.intensity() <= 0.001f || !DepthOfFieldEffect.hasCapturedDepth() || minecraft.level == null
                || minecraft.level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD) return;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Camera camera = minecraft.gameRenderer.mainCamera();
        float angle = (float) Math.toRadians(camera.attributeProbe()
                .getValue(EnvironmentAttributes.SUN_ANGLE, partialTick));
        Vector3f sun = new Vector3f(-(float) Math.sin(angle), (float) Math.cos(angle), 0);
        float forward = sun.dot(camera.forwardVector());
        if (forward <= 0.01f) return;
        float x = 0.5f + sun.dot(new Vector3f(camera.leftVector()).negate())
                * DepthOfFieldEffect.projectionX() / (2 * forward);
        float y = 0.5f + sun.dot(camera.upVector()) * DepthOfFieldEffect.projectionY() / (2 * forward);
        if (x < 0 || x > 1 || y < 0 || y > 1) return;
        if (pipeline == null) {
            pipeline = ScreenPass.pipeline("flare", BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DistanceSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("FlareSettings", UniformType.UNIFORM_BUFFER).build(), GpuFormat.RGBA8_UNORM, null);
            settings = RenderSystem.getDevice().createBuffer(() -> "vector3_flare_settings",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 48)
                    .putVec4(value.intensity(), value.threshold(), value.ghosts(), value.halo())
                    .putVec4(value.chromatic(), 1f / main.width, 1f / main.height,
                            (float) main.width / main.height)
                    .putVec4(x, y, Math.clamp((sun.y + 0.04f) / 0.12f, 0, 1)
                            * (1 - minecraft.level.getRainLevel(partialTick)), 0).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(settings.slice(), data);
        }
        RenderTarget target = ScreenPass.scratch(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "vector3_flare", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), ScreenPass.linear());
            pass.setUniform("DistanceSampler", DepthOfFieldEffect.distanceView(), ScreenPass.nearest());
            pass.setUniform("FlareSettings", settings);
            pass.draw(3, 1, 0, 0);
        }
        ScreenPass.copy(target, main);
    }
}
