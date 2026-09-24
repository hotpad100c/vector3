package ml.mypals.vectorthree.flashback;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImFontAtlas;
import imgui.moulberry90.ImFontConfig;
import imgui.moulberry90.ImFontGlyphRangesBuilder;
import ml.mypals.vectorthree.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.stb.STBTTFontinfo;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

import static org.lwjgl.stb.STBTruetype.stbtt_FindGlyphIndex;
import static org.lwjgl.stb.STBTruetype.stbtt_GetFontOffsetForIndex;
import static org.lwjgl.stb.STBTruetype.stbtt_InitFont;

/**
 * vector3's own UI icons, from {@code assets/vector3/ui/icons.ttf}, merged into Flashback's ImGui font
 * next to its Material icons (same 20px size and offset, tinted by the text colour). The font is
 * optional and may hold any subset of the codepoints below; every missing glyph falls back.
 * <ul>
 *   <li>U+F500 Shape track, U+F501 Look To track</li>
 *   <li>U+F510 onward: one per shape type, in {@link #SHAPE_TYPES} order
 *       (box F510, sphere F511, face_circle F512, ... area F51F)</li>
 * </ul>
 */
public final class VectorIcons {
    public static final char SHAPE_TRACK = '\uF500';
    public static final char LOOK_TO_TRACK = '\uF501';
    private static final char FIRST_SHAPE_TYPE = '\uF510';
    private static final List<String> SHAPE_TYPES = List.of("box", "sphere", "face_circle", "cylinder", "cone",
            "line", "line_strip", "text", "block", "item", "entity", "obj", "arrow", "image", "video", "area");
    private static final char FIRST = '\uF500';
    private static final char LAST = '\uF5FF';
    private static final Identifier FONT = Vector3.id("ui/icons.ttf");

    private static final BitSet present = new BitSet();
    private static @Nullable ImFontConfig lastConfig;

    private VectorIcons() {}

    /** The icon if the font has it, otherwise {@code fallback}. */
    public static String icon(char glyph, String fallback) {
        return present.get(glyph) ? String.valueOf(glyph) : fallback;
    }

    /** {@code label} prefixed with the shape type's icon, when the font has one. */
    public static String withShapeIcon(@Nullable String shapeType, String label) {
        int index = shapeType == null ? -1 : SHAPE_TYPES.indexOf(shapeType);
        if (index < 0) return label;
        char glyph = (char) (FIRST_SHAPE_TYPE + index);
        return present.get(glyph) ? glyph + " " + label : label;
    }

    /** Called while Flashback builds its font atlas, right after it merges the Material icons. */
    public static void mergeInto(ImFontAtlas atlas) {
        present.clear();
        byte[] bytes = readFont();
        if (bytes == null) return;
        ImFontGlyphRangesBuilder ranges = new ImFontGlyphRangesBuilder();
        ByteBuffer data = ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
        STBTTFontinfo info = STBTTFontinfo.malloc();
        try {
            if (!stbtt_InitFont(info, data, stbtt_GetFontOffsetForIndex(data, 0))) {
                Vector3.LOGGER.warn("Icon font {} is not a readable TrueType/OpenType font", FONT);
                return;
            }
            for (char glyph = FIRST; glyph <= LAST; glyph++) {
                if (stbtt_FindGlyphIndex(info, glyph) != 0) {
                    present.set(glyph);
                    ranges.addChar(glyph);
                }
            }
        } finally {
            info.free();
        }
        if (present.isEmpty()) return;

        // The atlas keeps pointing into the config until it is built, so it lives until the next rebuild.
        if (lastConfig != null) lastConfig.destroy();
        lastConfig = new ImFontConfig();
        lastConfig.setMergeMode(true);
        lastConfig.setOversampleH(2);
        lastConfig.setOversampleV(2);
        lastConfig.setGlyphOffset(0, (int) (5 * ReplayUI.getUiScale()));
        atlas.addFontFromMemoryTTF(bytes, (int) (20 * ReplayUI.getUiScale()), lastConfig, ranges.buildRanges());
    }

    private static byte @Nullable [] readFont() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FONT);
        if (resource.isEmpty()) return null;
        try (InputStream stream = resource.get().open()) {
            return stream.readAllBytes();
        } catch (Exception exception) {
            Vector3.LOGGER.warn("Could not read icon font {}", FONT, exception);
            return null;
        }
    }
}
