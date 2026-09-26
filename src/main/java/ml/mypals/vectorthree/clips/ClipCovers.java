package ml.mypals.vectorthree.clips;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** The icon.png Flashback saves in every replay, as ImGui textures for the clip bars. */
final class ClipCovers {
    record Cover(DynamicTexture texture, float aspect) {
        // Asked every frame: the renderer drops ids of textures a frame didn't draw.
        long textureId() {
            return ReplayUI.imguiRenderer.getTextureId(texture.getTextureView());
        }
    }

    private static final Map<String, Cover> COVERS = new HashMap<>();
    private static final Cover NONE = new Cover(null, 1);

    private ClipCovers() {}

    static @Nullable Cover of(String source) {
        Cover cover = COVERS.computeIfAbsent(source, ClipCovers::load);
        return cover == NONE ? null : cover;
    }

    private static Cover load(String source) {
        try (FileSystem zip = FileSystems.newFileSystem(Path.of(source))) {
            Path icon = zip.getPath("/icon.png");
            if (!Files.exists(icon)) return NONE;
            try (InputStream stream = Files.newInputStream(icon)) {
                NativeImage image = NativeImage.read(stream);
                float aspect = (float) image.getWidth() / Math.max(1, image.getHeight());
                DynamicTexture texture = new DynamicTexture(() -> "Vector3 clip cover " + source, image);
                return new Cover(texture, aspect);
            }
        } catch (Exception exception) {
            return NONE;
        }
    }
}
