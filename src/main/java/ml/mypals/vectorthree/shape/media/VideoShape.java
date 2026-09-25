package ml.mypals.vectorthree.shape.media;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.basics.tags.EmptyMesh;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.Vector3;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import ml.mypals.vectorthree.shape.ShapeTrackRegistry;
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
        if (source == null) return;
        if (!source.isReady()) {
            drawProgress(builder, source.failed());
            return;
        }
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
        if (source.isLoading()) drawProgress(builder, false);
    }

    private static final int TRACK = 0x90181818, FILL = 0xE650C8FF, FAILED = 0xE6E04040;
    private static Identifier white;

    // No percentage is available from ffmpeg while opening or seeking, so the bar sweeps.
    private void drawProgress(VertexBuilder builder, boolean failed) {
        float halfWidth = aspect * 0.5f * 0.8f, y = sized ? -0.5f + 0.08f : 0, half = 0.03f;
        float sweep = (System.nanoTime() % 1_400_000_000L) / 1_400_000_000f;
        float segment = 0.3f, start = -segment + sweep * (1 + segment);
        float from = -halfWidth + Math.max(0, start) * halfWidth * 2;
        float to = -halfWidth + Math.min(1, start + segment) * halfWidth * 2;
        SubmitNodeStorage submits = new SubmitNodeStorage();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(builder.getPositionMatrix());
        submits.submitCustomGeometry(poseStack, ShapeTrackRegistry.imageType(white(), seeThrough), (pose, consumer) -> {
            box(consumer, pose, -halfWidth, y - half, -half, halfWidth, y + half, half, TRACK);
            float grow = 0.004f;
            if (failed) box(consumer, pose, -halfWidth - grow, y - half - grow, -half - grow, halfWidth + grow, y + half + grow, half + grow, FAILED);
            else if (to > from) box(consumer, pose, from, y - half - grow, -half - grow, to, y + half + grow, half + grow, FILL);
        });
        IrisBypassTarget.renderFeatures(() -> Helpers.renderFeatures(Minecraft.getInstance(), submits));
    }

    private static Identifier white() {
        if (white == null) {
            white = Vector3.id("video_progress_white");
            DynamicTexture texture = new DynamicTexture(() -> "Vector3 video progress", 1, 1, false);
            texture.getPixels().setPixelABGR(0, 0, 0xFFFFFFFF);
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(white, texture);
        }
        return white;
    }

    private static void box(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        face(c, p, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(c, p, argb, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        face(c, p, argb, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        face(c, p, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(c, p, argb, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(c, p, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void face(VertexConsumer c, PoseStack.Pose p, int argb, float... v) {
        for (int i = 0; i < 12; i += 3) c.addVertex(p, v[i], v[i + 1], v[i + 2]).setUv(0, 0).setColor(argb);
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
