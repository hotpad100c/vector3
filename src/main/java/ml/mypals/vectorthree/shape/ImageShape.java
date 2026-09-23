package ml.mypals.vectorthree.shape;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.tags.EmptyMesh;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ImageShape extends Shape implements EmptyMesh {
    private record Texture(Identifier id, float aspect) {}

    private static final Map<Path, Texture> TEXTURES = new HashMap<>();
    private final Texture texture;

    public ImageShape(String file, Color color, boolean seeThrough) {
        super(RenderingType.BATCH, transformer -> {}, color, Vec3.ZERO, seeThrough);
        texture = load(file);
        generateRawGeometry(false);
    }

    private static Texture load(String file) {
        if (file == null || file.isBlank()) return null;
        try {
            Path path = Path.of(file);
            if (!path.isAbsolute()) path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
            path = path.toAbsolutePath().normalize();
            final Path imagePath = path;
            Texture cached = TEXTURES.get(path);
            if (cached != null) return cached;
            try (InputStream stream = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(stream);
                Identifier id = Vector3.id("local_image/" + Integer.toUnsignedString(path.toString().hashCode(), 36));
                Minecraft.getInstance().getTextureManager().register(id,
                        new DynamicTexture(() -> "Vector3 image " + imagePath, image));
                Texture loaded = new Texture(id, (float) image.getWidth() / Math.max(1, image.getHeight()));
                TEXTURES.put(path, loaded);
                return loaded;
            } catch (IOException | RuntimeException exception) {
                Vector3.LOGGER.warn("Could not load image file {}", path, exception);
                return null;
            }
        }catch (Exception exception){
            Vector3.LOGGER.warn("Could not parse image path {}", file, exception);
            return null;
        }

    }

    public static void clearTextures() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Texture texture : TEXTURES.values()) minecraft.getTextureManager().release(texture.id());
        TEXTURES.clear();
    }

    @Override
    protected void generateRawGeometry(boolean regenerate) {
        float halfWidth = texture == null ? 0.5f : texture.aspect() * 0.5f;
        modelVertexes = List.of(new Vec3(-halfWidth, -0.5, 0), new Vec3(halfWidth, -0.5, 0),
                new Vec3(halfWidth, 0.5, 0), new Vec3(-halfWidth, 0.5, 0));
        indexBuffer = new int[] {0, 1, 2, 0, 2, 3};
    }

    @Override
    protected void drawInternal(VertexBuilder builder) {
        if (texture == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        SubmitNodeStorage submits = new SubmitNodeStorage();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(builder.getPositionMatrix());
        int argb = baseColor.getRGB();
        float halfWidth = texture.aspect() * 0.5f;
        submits.submitCustomGeometry(poseStack, ShapeTrackRegistry.imageType(texture.id(), seeThrough),
                (pose, consumer) -> quad(pose, consumer, halfWidth, argb));
        IrisBypassTarget.renderFeatures(() -> Helpers.renderFeatures(minecraft, submits));
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, float halfWidth, int argb) {
        vertex(consumer, pose, -halfWidth, -0.5f, 0, 1, argb);
        vertex(consumer, pose, halfWidth, -0.5f, 1, 1, argb);
        vertex(consumer, pose, halfWidth, 0.5f, 1, 0, argb);
        vertex(consumer, pose, -halfWidth, 0.5f, 0, 0, argb);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
            float x, float y, float u, float v, int argb) {
        consumer.addVertex(pose, x, y, 0).setUv(u, v).setColor(argb);
    }
}
