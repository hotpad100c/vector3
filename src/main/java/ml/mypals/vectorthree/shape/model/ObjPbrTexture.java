package ml.mypals.vectorthree.shape.model;

import com.mojang.blaze3d.platform.NativeImage;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;

/**
 * An OBJ colour texture that knows its normal and specular maps. With a shader pack, Iris asks its PBR loader
 * registry for them by texture class (see IrisHooks#registerObjPbr); Iris owns and closes what it is given, so each
 * request decodes fresh copies.
 */
public final class ObjPbrTexture extends DynamicTexture {
    private final @Nullable ObjModel.TextureRef normal;
    private final @Nullable ObjModel.TextureRef specular;

    ObjPbrTexture(NativeImage image, @Nullable ObjModel.TextureRef normal, @Nullable ObjModel.TextureRef specular) {
        super(() -> "Vector3 OBJ texture", image);
        this.normal = normal;
        this.specular = specular;
    }

    public @Nullable AbstractTexture loadNormal() {
        return load(normal);
    }

    public @Nullable AbstractTexture loadSpecular() {
        return load(specular);
    }

    private static @Nullable AbstractTexture load(@Nullable ObjModel.TextureRef ref) {
        if (ref == null) return null;
        try {
            return new DynamicTexture(() -> "Vector3 OBJ PBR texture", ref.read());
        } catch (Exception exception) {
            Vector3.LOGGER.warn("Could not load OBJ PBR texture {}", ref, exception);
            return null;
        }
    }
}
