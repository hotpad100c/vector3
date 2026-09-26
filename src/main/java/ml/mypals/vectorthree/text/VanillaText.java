package ml.mypals.vectorthree.text;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import ml.mypals.vectorthree.mixin.area.RenderSetupAccessor;
import ml.mypals.vectorthree.mixin.area.RenderTypeAccessor;
import ml.mypals.vectorthree.mixin.area.TextureBindingAccessor;
import ml.mypals.vectorthree.render.TextGlow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;


public final class VanillaText {
    /** Strength in eighths rides in the packed light, which the glow shader reads back from UV2. */
    public record Glow(boolean fill, @Nullable Integer fillColor, int strength) {}

    private record Key(Identifier texture, boolean grayscale, Font.DisplayMode mode) {}

    private record Rings(List<Font.PreparedText> rings) implements Font.PreparedText {
        @Override
        public void visit(Font.GlyphVisitor visitor) {
            for (Font.PreparedText ring : rings) ring.visit(visitor);
        }

        @Override
        public @Nullable ScreenRectangle bounds() {
            return rings.getLast().bounds();
        }
    }

    public static @Nullable Glow glow;
    public static float outlineWidth = 1;
    public static float outlineScale = 1;

    private static final Map<Key, RenderType> GLOW_TYPES = new HashMap<>();
    private static final Map<String, RenderPipeline> GLOW_PIPELINES = new HashMap<>();

    private VanillaText() {}

    /** Vanilla's outline is one ring one text pixel out; wider outlines stack rings out to the width. */
    public static Font.PreparedText rings(Supplier<Font.PreparedText> ring) {
        float width = outlineWidth;
        if (width == 1) return ring.get();
        int count = Math.max(1, (int) Math.ceil(width));
        List<Font.PreparedText> rings = new ArrayList<>(count);
        try {
            for (int i = 1; i <= count; i++) {
                outlineScale = width * i / count;
                rings.add(ring.get());
            }
        } finally {
            outlineScale = 1;
        }
        return rings.size() == 1 ? rings.getFirst() : new Rings(rings);
    }

    public static int glowFill(Glow glow, int argb) {
        int alpha = argb >>> 24;
        if (!glow.fill()) return alpha << 24;
        if (glow.fillColor() == null) return argb;
        return (alpha * (glow.fillColor() >>> 24) / 255) << 24 | (glow.fillColor() & 0xFFFFFF);
    }

    public static @Nullable RenderType glowType(GlyphRenderTypes types, Font.DisplayMode mode) {
        Identifier texture = texture(types.normal());
        if (texture == null) return null;
        boolean grayscale = types.guiPipeline() == RenderPipelines.GUI_TEXT_GRAYSCALE;
        return GLOW_TYPES.computeIfAbsent(new Key(texture, grayscale, mode), key -> RenderType.create(
                "vector3_text_glow",
                RenderSetup.builder(pipeline(grayscale, mode))
                        .withTexture("Sampler0", texture, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                        .createRenderSetup()));
    }

    private static @Nullable Identifier texture(RenderType type) {
        Object binding = ((RenderSetupAccessor) (Object) ((RenderTypeAccessor) type).vector3$state()).vector3$textures().get("Sampler0");
        return binding instanceof TextureBindingAccessor accessor ? accessor.vector3$location() : null;
    }

    // Tested against the copied scene depth like the text itself (polygon offset included) but never writing it.
    private static RenderPipeline pipeline(boolean grayscale, Font.DisplayMode mode) {
        String name = "text_glow" + (grayscale ? "_grayscale" : "") + "_" + mode.name().toLowerCase(java.util.Locale.ROOT);
        return GLOW_PIPELINES.computeIfAbsent(name, n -> {
            DepthStencilState depth = switch (mode) {
                case SEE_THROUGH -> new DepthStencilState(CompareOp.ALWAYS_PASS, false);
                case POLYGON_OFFSET -> new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false, 1, 10);
                default -> new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false);
            };
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("vector3", n))
                    .withVertexShader(Identifier.fromNamespaceAndPath("vector3", "core/text_glow"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("vector3", "core/text_glow"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
                    .withColorTargetState(new ColorTargetState(java.util.Optional.of(BlendFunction.TRANSLUCENT), TextGlow.FORMAT,
                            ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(depth)
                    .withCull(false);
            if (grayscale) builder.withShaderDefine("GRAYSCALE");
            return RenderPipelines.register(builder.build());
        });
    }
}
