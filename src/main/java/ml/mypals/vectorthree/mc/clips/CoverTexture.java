package ml.mypals.vectorthree.mc.clips;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/** The icon.png Flashback saves in every replay, as a texture. */
public record CoverTexture(DynamicTexture texture, float aspect) {
    public GpuTextureView view() {
        return texture.getTextureView();
    }

    public boolean alive() {
        try {
            texture.getTextureView();
            return true;
        } catch (IllegalStateException freed) {
            return false;
        }
    }

    public void close() {
        texture.close();
    }

    public static @Nullable CoverTexture load(String source) {
        try (FileSystem zip = FileSystems.newFileSystem(Path.of(source))) {
            Path icon = zip.getPath("/icon.png");
            if (!Files.exists(icon)) return null;
            try (InputStream stream = Files.newInputStream(icon)) {
                NativeImage image = NativeImage.read(stream);
                float aspect = (float) image.getWidth() / Math.max(1, image.getHeight());
                return new CoverTexture(new DynamicTexture(() -> "Vector3 clip cover " + source, image), aspect);
            }
        } catch (Exception exception) {
            return null;
        }
    }
}
