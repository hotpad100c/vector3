package ml.mypals.vectorthree.text;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ml.mypals.ryansrenderingkit.builders.vertexBuilders.VertexBuilder;
import ml.mypals.ryansrenderingkit.shape.minecraftBuiltIn.TextShape;
import ml.mypals.ryansrenderingkit.utils.Helpers;
import ml.mypals.vectorthree.render.IrisBypassTarget;
import ml.mypals.vectorthree.render.TextGlow;
import ml.mypals.vectorthree.shape.text.FontTextShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays out and draws a text shape's styled lines with an {@link SdfFont}. Works in the same text units
 * as vanilla (the shape's pose already applies billboarding and the 1/64 scale), so a line is 9 units
 * tall with 1.25× line spacing, centered on the shape. Per-character style comes from the parsed
 * FormattedCharSequence: color, bold, italic, underline and strikethrough.
 */
public final class SdfTextRenderer {
    private static final float GLYPH_HEIGHT = 9f;
    private static final float LINE_ADVANCE = GLYPH_HEIGHT * 1.25f;
    private static final float ITALIC_SHEAR = 0.2f;
    private static final float BOLD_EXTRA_ADVANCE = SdfFont.SDF_PIXEL_HEIGHT * 0.02f;
    private static final float SHADOW_OFFSET = 1f;
    /** Lifts the main text toward the viewer (+z in text space) so it never z-fights its shadow. */
    private static final float MAIN_Z = 0.1f;
    private static final float BAR_THICKNESS = GLYPH_HEIGHT * 0.07f;
    private static final int FLAG_BOLD = 1;
    private static final int FLAG_OUTLINE = 2;

    private record Quad(int page, float[] x, float[] y, float z, float u0, float v0, float u1, float v1,
            int argb, int style0, int style1) {}

    private record Char(int codepoint, Style style) {}

    /** How the glow pass recolors a line: glowing fill (optionally recolored), glowing outline, strength in eighths. */
    private record Glow(boolean fill, Integer fillColor, boolean outline, int strength, float spread) {}

    private SdfTextRenderer() {}

    /** Draws the shape's text; returns its layout bounds {minX, minY, maxX, maxY} in text units. */
    public static float[] draw(TextShape shape, VertexBuilder builder, SdfFont font) {
        FormattedCharSequence[] lines = shape.getRenderMessages();
        float unit = GLYPH_HEIGHT / (font.ascent - font.descent);
        float top = -lines.length * LINE_ADVANCE / 2f;
        boolean shadow = shape.shadow && !shape.outline;
        Integer outlineRgb = shape instanceof FontTextShape fontShape ? fontShape.outlineColor : null;
        int outlineWidth = Math.clamp(Math.round((shape instanceof FontTextShape fontShape ? fontShape.outlineWidth : 1) * 32), 0, 255);
        List<Quad> quads = new ArrayList<>();
        List<Quad> glowQuads = new ArrayList<>();
        Glow glow = glowOf(shape);
        float halfWidth = 0;

        for (int i = 0; i < lines.length; i++) {
            Color lineColor = i < shape.colors.size() ? shape.colors.get(i) : shape.baseColor;
            List<Char> chars = new ArrayList<>();
            lines[i].accept((index, style, codepoint) -> {
                chars.add(new Char(codepoint, style));
                return true;
            });
            float baseline = top + i * LINE_ADVANCE + (LINE_ADVANCE - GLYPH_HEIGHT) / 2f + font.ascent * unit;
            float startX = -lineWidth(font, chars) * unit / 2f;
            halfWidth = Math.max(halfWidth, -startX);
            if (shadow) layoutLine(font, chars, unit, startX + SHADOW_OFFSET, baseline + SHADOW_OFFSET, 0,
                    lineColor, true, false, null, outlineWidth, null, quads);
            layoutLine(font, chars, unit, startX, baseline, shadow ? MAIN_Z : 0, lineColor, false, shape.outline,
                    outlineRgb, outlineWidth, null, quads);
            if (glow != null) layoutLine(font, chars, unit, startX, baseline, shadow ? MAIN_Z : 0, lineColor, false,
                    glow.outline(), outlineRgb, outlineWidth, glow, glowQuads);
        }
        float[] bounds = {-halfWidth, top, halfWidth, -top};
        if (quads.isEmpty()) return bounds;
        font.flush();

        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(builder.getPositionMatrix());
        SubmitNodeStorage submits = new SubmitNodeStorage();
        quads.stream().mapToInt(Quad::page).distinct().forEach(page ->
                submits.submitCustomGeometry(poseStack, SdfTextRenderTypes.get(font.pageTexture(page), shape.seeThrough),
                        (pose, consumer) -> {
                            for (Quad quad : quads) if (quad.page() == page) emit(pose, consumer, quad);
                        }));
        IrisBypassTarget.renderFeatures(() -> Helpers.renderFeatures(minecraft, submits));
        if (!glowQuads.isEmpty()) {
            SubmitNodeStorage glowSubmits = new SubmitNodeStorage();
            glowQuads.stream().mapToInt(Quad::page).distinct().forEach(page ->
                    glowSubmits.submitCustomGeometry(poseStack, SdfTextRenderTypes.getGlow(font.pageTexture(page), shape.seeThrough),
                            (pose, consumer) -> {
                                for (Quad quad : glowQuads) if (quad.page() == page) emit(pose, consumer, quad);
                            }));
            TextGlow.render(glow.spread(), () -> Helpers.renderFeatures(minecraft, glowSubmits));
        }
        return bounds;
    }

    private static Glow glowOf(TextShape shape) {
        if (!(shape instanceof FontTextShape fontShape)) return null;
        boolean outline = shape.outline && fontShape.outlineGlow;
        int strength = Math.round(fontShape.glowStrength * 8);
        if (!fontShape.glow && !outline || strength <= 0) return null;
        return new Glow(fontShape.glow, fontShape.glowColor, outline, Math.min(strength, 63), fontShape.glowSpread);
    }

    private static float lineWidth(SdfFont font, List<Char> chars) {
        float width = 0;
        int previous = -1;
        for (Char c : chars) {
            if (previous >= 0) width += font.kerning(previous, c.codepoint());
            width += font.glyph(c.codepoint()).advance();
            if (c.style().isBold()) width += BOLD_EXTRA_ADVANCE;
            previous = c.codepoint();
        }
        return width;
    }

    private static void layoutLine(SdfFont font, List<Char> chars, float unit, float x, float baseline, float z,
            Color lineColor, boolean isShadow, boolean outline, Integer outlineOverride, int outlineWidth, Glow glow,
            List<Quad> out) {
        float pen = 0;
        int previous = -1;
        for (Char c : chars) {
            if (previous >= 0) pen += font.kerning(previous, c.codepoint());
            previous = c.codepoint();
            Style style = c.style();
            SdfFont.Glyph glyph = font.glyph(c.codepoint());
            float advance = glyph.advance() + (style.isBold() ? BOLD_EXTRA_ADVANCE : 0);

            int argb = color(style, lineColor);
            if (isShadow) argb = scaleRgb(argb, 0.25f);
            int outlineRgb = outlineOverride != null ? outlineOverride : scaleRgb(argb, 0.25f);
            int flags = (style.isBold() ? FLAG_BOLD : 0) | (outline ? FLAG_OUTLINE : 0);
            if (glow != null) {
                // A non-glowing fill stays in as black, cutting the glyph out of its outline's glow.
                int alpha = argb >>> 24;
                if (!glow.fill()) argb = alpha << 24;
                else if (glow.fillColor() != null) {
                    argb = (alpha * (glow.fillColor() >>> 24) / 255) << 24 | (glow.fillColor() & 0xFFFFFF);
                }
                flags |= glow.strength() << 2;
            }
            // Outline colour as RGB565, leaving a byte for the outline width in 1/32 steps.
            int style0 = ((outlineRgb >> 8) & 0xF800) | ((outlineRgb >> 5) & 0x07E0) | ((outlineRgb >> 3) & 0x001F);
            int style1 = outlineWidth | (flags << 8);
            float shear = style.isItalic() ? ITALIC_SHEAR : 0;

            if (glyph.visible()) {
                float left = x + (pen + glyph.x0()) * unit, right = x + (pen + glyph.x1()) * unit;
                float top = baseline + glyph.y0() * unit, bottom = baseline + glyph.y1() * unit;
                out.add(new Quad(glyph.page(),
                        sheared(new float[]{left, left, right, right}, new float[]{top, bottom, bottom, top}, baseline, shear),
                        new float[]{top, bottom, bottom, top}, z,
                        glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(), argb, style0, style1));
            }
            float barLeft = x + pen * unit, barRight = x + (pen + advance) * unit;
            if (style.isUnderlined()) {
                out.add(bar(font, barLeft, barRight, baseline - font.descent * unit * 0.35f, z, argb, style0, style1));
            }
            if (style.isStrikethrough()) {
                out.add(bar(font, barLeft, barRight, baseline - font.ascent * unit * 0.3f, z, argb, style0, style1));
            }
            pen += advance;
        }
    }

    private static Quad bar(SdfFont font, float left, float right, float centerY, float z, int argb, int style0, int style1) {
        SdfFont.Glyph solid = font.solid();
        float top = centerY - BAR_THICKNESS / 2, bottom = centerY + BAR_THICKNESS / 2;
        return new Quad(solid.page(), new float[]{left, left, right, right}, new float[]{top, bottom, bottom, top}, z,
                solid.u0(), solid.v0(), solid.u1(), solid.v1(), argb, style0, style1 & ~(FLAG_BOLD << 8));
    }

    private static float[] sheared(float[] xs, float[] ys, float baseline, float shear) {
        if (shear == 0) return xs;
        for (int i = 0; i < xs.length; i++) xs[i] += (baseline - ys[i]) * shear;
        return xs;
    }

    private static int color(Style style, Color lineColor) {
        int alpha = lineColor.getAlpha() << 24;
        TextColor textColor = style.getColor();
        return textColor != null ? alpha | (textColor.getValue() & 0xFFFFFF) : lineColor.getRGB();
    }

    private static int scaleRgb(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor), g = (int) (((argb >> 8) & 0xFF) * factor), b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, Quad quad) {
        float[] us = {quad.u0(), quad.u0(), quad.u1(), quad.u1()};
        float[] vs = {quad.v0(), quad.v1(), quad.v1(), quad.v0()};
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, quad.x()[i], quad.y()[i], quad.z()).setUv(us[i], vs[i]).setColor(quad.argb())
                    .setUv2(quad.style0(), quad.style1());
        }
    }
}
