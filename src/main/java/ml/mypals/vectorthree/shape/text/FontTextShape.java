package ml.mypals.vectorthree.shape.text;

import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.collision.RayModelIntersection;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import ml.mypals.vectorthree.text.SdfFont;
import ml.mypals.vectorthree.text.SdfTextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FontTextShape extends TextShape {
    /** Pick padding around the text bounds, in text units (1/64 block before scaling). */
    private static final float PICK_PADDING = 1f;
    private static final int[] PICK_INDICES = {0, 1, 2, 0, 2, 3, 0, 2, 1, 0, 3, 2};

    public String font;
    public int outlineColor = 0xFF000000;
    public boolean glow;
    public boolean outlineGlow;
    public float glowStrength = TextSettings.DEFAULT_GLOW_STRENGTH;
    public Integer glowColor;

    private Matrix4f lastPose;
    private Vec3 lastCamera;
    private float minX, minY, maxX, maxY;

    public FontTextShape(TextSettings settings, Color color, boolean seeThrough) {
        super(Shape.RenderingType.BATCH, transformer -> {}, Vec3.ZERO,
                Arrays.asList(settings.value().split("\\R", -1)), List.of(color),
                BillBoardMode.valueOf(settings.billboard()), seeThrough, settings.shadow(), settings.outline());
        font = settings.fontOrDefault();
    }

    /** A font setting naming a .ttf/.otf/.ttc file, or a definition backed by one, uses vector3's SDF
     *  renderer; bitmap fonts stay vanilla. Either way the drawn bounds are kept for picking. */
    @Override
    protected void drawInternal(VertexBuilder builder) {
        SdfFont sdf = SdfFont.get(font);
        float[] bounds;
        if (sdf == null) {
            super.drawInternal(builder);
            bounds = vanillaBounds();
        } else {
            bounds = SdfTextRenderer.draw(this, builder, sdf);
        }
        minX = bounds[0];
        minY = bounds[1];
        maxX = bounds[2];
        maxY = bounds[3];
        lastPose = new Matrix4f(builder.getPositionMatrix());
        lastCamera = Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    /** Same centered layout TextShape uses: 9-unit lines at 1.25× spacing. */
    private float[] vanillaBounds() {
        var mcFont = Minecraft.getInstance().font;
        float width = 0;
        for (var line : getRenderMessages()) width = Math.max(width, mcFont.width(line));
        float halfHeight = contents.size() * 9 * 1.25f / 2f;
        return new float[]{-width / 2f, -halfHeight, width / 2f, halfHeight};
    }

    /** Ray distance to the text's last drawn quad (both faces), or -1 when missed or never drawn. */
    public double hitDistance(RayModelIntersection.Ray ray) {
        if (lastPose == null) return -1;
        float[][] corners = {{minX - PICK_PADDING, minY - PICK_PADDING}, {maxX + PICK_PADDING, minY - PICK_PADDING},
                {maxX + PICK_PADDING, maxY + PICK_PADDING}, {minX - PICK_PADDING, maxY + PICK_PADDING}};
        List<Vec3> quad = new ArrayList<>(4);
        for (float[] corner : corners) {
            Vector3f world = lastPose.transformPosition(new Vector3f(corner[0], corner[1], 0), new Vector3f());
            quad.add(lastCamera.add(world.x, world.y, world.z));
        }
        RayModelIntersection.HitResult hit = RayModelIntersection.rayIntersectsModel(ray, quad, PICK_INDICES);
        return hit.hit ? hit.distance : -1;
    }
}
