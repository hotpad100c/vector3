package ml.mypals.vectorthree.mc.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** What 26.2's {@link RenderTarget} lacks next to its {@code copyDepthFrom}. */
public final class RenderTargets {
    private RenderTargets() {}

    /** Copies {@code source}'s color into {@code destination}, which must be at least as large. */
    public static void copyColor(RenderTarget source, RenderTarget destination) {
        RenderSystem.assertOnRenderThread();
        GpuTexture from = Objects.requireNonNull(source.getColorTexture(), "source has no color texture");
        GpuTexture to = Objects.requireNonNull(destination.getColorTexture(), "destination has no color texture");
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(from, to, 0, 0, 0, 0, 0,
                destination.width, destination.height);
    }

    /**
     * Runs {@code draw} with render-type draws sent to {@code target} instead of their own output target, through
     * {@link RenderSystem#outputColorTextureOverride} (how 26.2 picks the pass attachments for those draws).
     * A null {@code target} runs {@code draw} unchanged.
     */
    public static void drawInto(@Nullable RenderTarget target, Runnable draw) {
        if (target == null) {
            draw.run();
            return;
        }
        GpuTextureView color = RenderSystem.outputColorTextureOverride, depth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.useDepth ? target.getDepthTextureView() : null;
        try {
            draw.run();
        } finally {
            RenderSystem.outputColorTextureOverride = color;
            RenderSystem.outputDepthTextureOverride = depth;
        }
    }
}
