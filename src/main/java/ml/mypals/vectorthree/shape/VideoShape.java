package ml.mypals.vectorthree.shape;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
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
import java.util.List;
import java.util.UUID;

public final class VideoShape extends Shape implements EmptyMesh {
    public static final int TICKS_PER_SECOND = 20;

    private final String file;
    private final VideoFrameSource source;
    private Identifier textureId;
    private DynamicTexture texture;
    private float aspect = 1.0f;
    private boolean sized;
    private int videoStartTick;
    private boolean autoPlay = true;
    private boolean loop = true;
    private float playbackSeconds;

    public VideoShape(String file, Color color, boolean seeThrough) {
        super(RenderingType.BATCH, transformer -> {}, color, Vec3.ZERO, seeThrough);
        this.file = file;
        source = VideoFrameSource.open(file);
        generateRawGeometry(false);
    }

    public void setPlayback(int videoStartTick, boolean autoPlay, boolean loop, float playbackSeconds) {
        this.videoStartTick = videoStartTick;
        this.autoPlay = autoPlay;
        this.loop = loop;
        this.playbackSeconds = playbackSeconds;
    }

    /** Video length in seconds, or 0 while the decoder is still opening / failed to open. */
    public double duration() {
        return source == null ? 0 : source.duration();
    }

    @Override
    protected void generateRawGeometry(boolean regenerate) {
        float halfWidth = aspect * 0.5f;
        modelVertexes = List.of(new Vec3(-halfWidth, -0.5, 0), new Vec3(halfWidth, -0.5, 0),
                new Vec3(halfWidth, 0.5, 0), new Vec3(-halfWidth, 0.5, 0));
        indexBuffer = new int[] {0, 1, 2, 0, 2, 3};
    }

    @Override
    protected void drawInternal(VertexBuilder builder) {
        if (source == null || !source.isReady()) return;
        if (!sized) {
            sized = true;
            aspect = (float) source.width() / source.height();
            textureId = Vector3.id("local_video/" + UUID.randomUUID());
            texture = new DynamicTexture(() -> "Vector3 video " + file, source.width(), source.height(), false);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            generateRawGeometry(true);
        }
        double duration = source.duration();
        double seconds;
        if (autoPlay) {
            seconds = Math.max(0, (TimelineWindow.getCursorTick() - videoStartTick) / (double) TICKS_PER_SECOND);
            if (duration > 0) seconds = loop ? seconds % duration : Math.min(seconds, duration);
        } else {
            seconds = Math.max(0, playbackSeconds);
            if (duration > 0) seconds = Math.min(seconds, duration);
        }
        source.requestSeconds(seconds);
        if (source.pollFrame(texture.getPixels())) texture.upload();

        Minecraft minecraft = Minecraft.getInstance();
        SubmitNodeStorage submits = new SubmitNodeStorage();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(builder.getPositionMatrix());
        int argb = baseColor.getRGB();
        float halfWidth = aspect * 0.5f;
        submits.submitCustomGeometry(poseStack, ShapeTrackRegistry.imageType(textureId, seeThrough),
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

    @Override
    public void discard() {
        if (source != null) source.close();
        if (textureId != null) Minecraft.getInstance().getTextureManager().release(textureId);
        super.discard();
    }
}
