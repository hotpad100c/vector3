package ml.mypals.vectorthree.flashback.fade.effects;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.shape.media.ImageDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Effect textures given either as a resource ID or as an image file on disk. */
public final class EffectTextures {
    private static final Map<Path, Optional<Identifier>> FILES = new HashMap<>();

    private EffectTextures() {}

    public static GpuTextureView view(String source) {
        if (source == null || source.isBlank()) return null;
        Identifier id = file(source.trim());
        if (id == null) {
            id = Identifier.tryParse(source.trim());
            if (id == null || Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) return null;
        }
        return Minecraft.getInstance().getTextureManager().getTexture(id).getTextureView();
    }

    private static Identifier file(String source) {
        Path path;
        try {
            path = Path.of(source);
            if (!path.isAbsolute()) path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
            path = path.toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
        Optional<Identifier> cached = FILES.get(path);
        if (cached != null) return cached.orElse(null);
        if (!Files.isRegularFile(path)) return null;
        Identifier id = null;
        try {
            NativeImage image = ImageDecoder.decode(path);
            id = Vector3.id("effect_texture/" + Integer.toUnsignedString(path.toString().hashCode(), 36));
            final Path imagePath = path;
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "Vector3 effect texture " + imagePath, image));
        } catch (IOException | RuntimeException exception) {
            Vector3.LOGGER.warn("Could not load effect texture {}", path, exception);
            id = null;
        }
        FILES.put(path, Optional.ofNullable(id));
        return id;
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Optional<Identifier> id : FILES.values()) id.ifPresent(minecraft.getTextureManager()::release);
        FILES.clear();
    }
}
