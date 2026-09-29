package ml.mypals.vectorthree.mc.camera;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Renders the world a second time from a keyframed camera, into a target of its own. The editor decides when and from where. */
public final class PreviewPass {
    public record Shot(Vec3 eye, Vec3 forward, Vec3 up, float fov, float aspect) {}

    private static @Nullable GameRenderer renderer;
    private static @Nullable RenderTarget image;
    private static boolean rendering;

    private PreviewPass() {}

    /** Called by the GameRenderer hook at the start of every frame. */
    public static void bind(GameRenderer gameRenderer) {
        renderer = gameRenderer;
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static boolean mainTargetReady() {
        if (renderer == null) return false;
        RenderTarget main = renderer.mainRenderTarget();
        return main.width > 0 && main.height > 0;
    }

    /** {@code applyCamera} moves the camera before the pass, {@code restoreCamera} puts everything back after it. */
    public static Shot render(Runnable applyCamera, Runnable restoreCamera) {
        GameRenderer renderer = PreviewPass.renderer;
        DeltaTracker deltaTracker = Minecraft.getInstance().getDeltaTracker();
        RenderTarget main = renderer.mainRenderTarget();
        rendering = true;
        EditorOverlays.hide();
        try {
            applyCamera.run();
            renderer.update(deltaTracker);
            renderer.extract(deltaTracker, true);
            renderer.render();
            Camera camera = renderer.mainCamera();
            Shot shot = new Shot(camera.position(), new Vec3(camera.forwardVector()), new Vec3(camera.upVector()),
                    camera.getFov(), (float) main.width / main.height);
            if (image == null || image.width != main.width || image.height != main.height) {
                release();
                image = new TextureTarget("vector3_camera_preview", main.width, main.height, GpuFormat.RGBA8_UNORM, null);
            }
            image.copyColorFrom(main);
            return shot;
        } finally {
            EditorOverlays.restore();
            restoreCamera.run();
            renderer.update(deltaTracker);
            renderer.extract(deltaTracker, true);
            rendering = false;
        }
    }

    public static @Nullable GpuTextureView textureView() {
        return image == null ? null : image.getColorTextureView();
    }

    public static float aspect() {
        return image == null ? 1 : (float) image.width / Math.max(1, image.height);
    }

    public static void release() {
        if (image != null) {
            image.destroyBuffers();
            image = null;
        }
    }
}
