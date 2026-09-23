package ml.mypals.vectorthree.text;

import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonParser;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTTFontinfo;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * A TrueType/OpenType font rendered as signed distance fields. Glyphs are rasterized on first use by
 * stb_truetype (already bundled with Minecraft) into 1024² atlas pages, so text stays sharp at any size
 * and outline/bold are just different thresholds in the shader instead of extra geometry.
 */
public final class SdfFont {
    /** Rasterization size of the distance field; the on-screen size is independent of this. */
    static final int SDF_PIXEL_HEIGHT = 48;
    private static final int PADDING = 6;
    private static final int ON_EDGE = 128;
    private static final float PIXEL_DIST_SCALE = (float) ON_EDGE / PADDING;
    private static final int PAGE_SIZE = 1024;
    private static final int SOLID_SIZE = 4;

    private static final Map<String, Optional<SdfFont>> CACHE = new HashMap<>();

    /** Metrics are in SDF pixels, y down, relative to the pen position on the baseline. */
    public record Glyph(float advance, float x0, float y0, float x1, float y1,
            int page, float u0, float v0, float u1, float v1) {
        public boolean visible() { return page >= 0; }
    }

    private static final class Page {
        final NativeImage image;
        final DynamicTexture texture;
        final Identifier id;
        int x;
        int y;
        int rowHeight;
        boolean dirty;

        Page(NativeImage image, DynamicTexture texture, Identifier id) {
            this.image = image;
            this.texture = texture;
            this.id = id;
        }
    }

    private final String key;
    private final ByteBuffer data;
    private final STBTTFontinfo info;
    private final float scale;
    final float ascent;
    final float descent;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final List<Page> pages = new ArrayList<>();
    private Glyph solid;

    private SdfFont(String key, ByteBuffer data, STBTTFontinfo info) {
        this.key = key;
        this.data = data;
        this.info = info;
        this.scale = stbtt_ScaleForPixelHeight(info, SDF_PIXEL_HEIGHT);
        int[] ascent = new int[1], descent = new int[1], lineGap = new int[1];
        stbtt_GetFontVMetrics(info, ascent, descent, lineGap);
        this.ascent = ascent[0] * scale;
        this.descent = descent[0] * scale;
    }

    /**
     * The SDF font a text shape's font setting names, or null to keep vanilla rendering. A setting is
     * either a font file (path or resource location), or a font definition such as
     * {@code minecraft:default} whose providers include a TrueType file — the case when a resource pack
     * replaces a font with a .ttf. Pure bitmap definitions (vanilla's pixel font) return null.
     */
    public static SdfFont get(String spec) {
        if (spec == null || spec.isBlank()) return null;
        return CACHE.computeIfAbsent(spec, SdfFont::load).orElse(null);
    }

    /** Whether {@link #get} would use SDF for this setting, without loading the font file. */
    public static boolean rendersAsSdf(String spec) {
        if (spec == null || spec.isBlank()) return false;
        if (isFontFile(spec)) return true;
        Identifier fontId = Identifier.tryParse(spec);
        return fontId != null && trueTypeFileOf(fontId, 0) != null;
    }

    /** Drops cached fonts, e.g. after resource packs change which file a font definition points to. */
    public static void clearCache() {
        CACHE.clear();
    }

    /** The TrueType file behind a font definition, following "reference" providers; highest pack first. */
    private static Identifier trueTypeFileOf(Identifier fontId, int depth) {
        if (depth > 8) return null;
        Identifier definition = Identifier.fromNamespaceAndPath(fontId.getNamespace(), "font/" + fontId.getPath() + ".json");
        var stack = Minecraft.getInstance().getResourceManager().getResourceStack(definition);
        for (int i = stack.size() - 1; i >= 0; i--) {
            try (var reader = stack.get(i).openAsReader()) {
                var providers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers");
                if (providers == null) continue;
                for (var element : providers) {
                    var provider = element.getAsJsonObject();
                    String type = provider.has("type") ? provider.get("type").getAsString() : "";
                    if (type.equals("ttf") && provider.has("file")) {
                        Identifier file = Identifier.tryParse(provider.get("file").getAsString());
                        if (file != null) return Identifier.fromNamespaceAndPath(file.getNamespace(), "font/" + file.getPath());
                    } else if (type.equals("reference") && provider.has("id")) {
                        Identifier referenced = Identifier.tryParse(provider.get("id").getAsString());
                        Identifier file = referenced == null ? null : trueTypeFileOf(referenced, depth + 1);
                        if (file != null) return file;
                    }
                }
            } catch (Exception malformed) {
                // A broken definition in one pack shouldn't hide a usable one in another.
            }
        }
        return null;
    }

    public static boolean isFontFile(String spec) {
        String lower = spec.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc");
    }

    private static Optional<SdfFont> load(String spec) {
        if (!isFontFile(spec)) {
            Identifier fontId = Identifier.tryParse(spec);
            Identifier file = fontId == null ? null : trueTypeFileOf(fontId, 0);
            return file == null ? Optional.empty() : load(file.toString());
        }
        try {
            byte[] bytes = readBytes(spec);
            if (bytes == null) {
                Vector3.LOGGER.warn("Font file not found: {}", spec);
                return Optional.empty();
            }
            ByteBuffer data = ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
            STBTTFontinfo info = STBTTFontinfo.create();
            if (!stbtt_InitFont(info, data, stbtt_GetFontOffsetForIndex(data, 0))) {
                Vector3.LOGGER.warn("Not a readable TrueType/OpenType font: {}", spec);
                return Optional.empty();
            }
            return Optional.of(new SdfFont(spec, data, info));
        } catch (Exception exception) {
            Vector3.LOGGER.warn("Could not load font {}", spec, exception);
            return Optional.empty();
        }
    }

    /** A file path (absolute or relative to the game directory), else a resource location. */
    private static byte[] readBytes(String spec) throws Exception {
        try {
            Path path = Path.of(spec);
            if (!path.isAbsolute()) path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
            if (Files.isRegularFile(path)) return Files.readAllBytes(path);
        } catch (RuntimeException notAPath) {
            // "namespace:path" isn't a valid Windows path; fall through to the resource lookup.
        }
        Identifier id = Identifier.tryParse(spec);
        if (id == null) return null;
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (InputStream stream = resource.get().open()) {
            return stream.readAllBytes();
        }
    }

    float kerning(int previous, int codepoint) {
        return stbtt_GetCodepointKernAdvance(info, previous, codepoint) * scale;
    }

    Glyph glyph(int codepoint) {
        Glyph cached = glyphs.get(codepoint);
        if (cached != null) return cached;
        int[] advance = new int[1], bearing = new int[1];
        stbtt_GetCodepointHMetrics(info, codepoint, advance, bearing);
        int[] width = new int[1], height = new int[1], xOff = new int[1], yOff = new int[1];
        ByteBuffer sdf = stbtt_GetCodepointSDF(info, scale, codepoint, PADDING, (byte) ON_EDGE,
                PIXEL_DIST_SCALE, width, height, xOff, yOff);
        Glyph glyph;
        if (sdf == null) {
            glyph = new Glyph(advance[0] * scale, 0, 0, 0, 0, -1, 0, 0, 0, 0);
        } else {
            try {
                int w = width[0], h = height[0];
                int[] slot = allocate(w, h);
                Page page = pages.get(slot[0]);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int value = sdf.get(y * w + x) & 0xFF;
                        page.image.setPixel(slot[1] + x, slot[2] + y, (value << 24) | 0xFFFFFF);
                    }
                }
                page.dirty = true;
                glyph = new Glyph(advance[0] * scale, xOff[0], yOff[0], xOff[0] + w, yOff[0] + h, slot[0],
                        (float) slot[1] / PAGE_SIZE, (float) slot[2] / PAGE_SIZE,
                        (float) (slot[1] + w) / PAGE_SIZE, (float) (slot[2] + h) / PAGE_SIZE);
            } finally {
                stbtt_FreeSDF(sdf);
            }
        }
        glyphs.put(codepoint, glyph);
        return glyph;
    }

    /** A fully-inside patch of the atlas, for underline/strikethrough bars. */
    Glyph solid() {
        if (solid == null) {
            int[] slot = allocate(SOLID_SIZE, SOLID_SIZE);
            Page page = pages.get(slot[0]);
            page.image.fillRect(slot[1], slot[2], SOLID_SIZE, SOLID_SIZE, 0xFFFFFFFF);
            page.dirty = true;
            float center = SOLID_SIZE / 2f;
            float u = (slot[1] + center) / PAGE_SIZE, v = (slot[2] + center) / PAGE_SIZE;
            solid = new Glyph(0, 0, 0, 0, 0, slot[0], u, v, u, v);
        }
        return solid;
    }

    /** Shelf-packs a w×h slot, starting a new page when the current one is full: {page, x, y}. */
    private int[] allocate(int w, int h) {
        Page page = pages.isEmpty() ? newPage() : pages.getLast();
        if (page.x + w + 1 > PAGE_SIZE) {
            page.x = 0;
            page.y += page.rowHeight + 1;
            page.rowHeight = 0;
        }
        if (page.y + h + 1 > PAGE_SIZE) page = newPage();
        int[] slot = {pages.size() - 1, page.x, page.y};
        page.x += w + 1;
        page.rowHeight = Math.max(page.rowHeight, h);
        return slot;
    }

    private Page newPage() {
        NativeImage image = new NativeImage(PAGE_SIZE, PAGE_SIZE, true);
        image.fillRect(0, 0, PAGE_SIZE, PAGE_SIZE, 0x00FFFFFF);
        int index = pages.size();
        DynamicTexture texture = new DynamicTexture(() -> "Vector3 SDF font " + key + " #" + index, image);
        Identifier id = Vector3.id("sdf_font/" + Integer.toUnsignedString(key.hashCode(), 36) + "/" + index);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        Page page = new Page(image, texture, id);
        pages.add(page);
        return page;
    }

    Identifier pageTexture(int page) {
        return pages.get(page).id;
    }

    /** Uploads glyphs rasterized since the last draw; call on the render thread before drawing. */
    void flush() {
        for (Page page : pages) {
            if (page.dirty) {
                page.texture.upload();
                page.dirty = false;
            }
        }
    }
}
