package ml.mypals.vectorthree.shape;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

import java.util.List;

public final class TextFormatting {
    private TextFormatting() {}

    public static FormattedCharSequence[] format(List<String> lines) {
        return format(lines, "minecraft:default");
    }

    public static FormattedCharSequence[] format(List<String> lines, String font) {
        return lines.stream().map(line -> formatLine(line, font))
                .toArray(FormattedCharSequence[]::new);
    }

    public static FormattedCharSequence formatLine(String text) {
        return formatLine(text, "minecraft:default");
    }

    public static FormattedCharSequence formatLine(String text, String font) {
        FontDescription selectedFont = font(font);
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text))
                        .result().map(component -> styled(component, selectedFont))
                        .orElseGet(() -> legacy(text, selectedFont));
            } catch (RuntimeException ignored) {
                try {
                    return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE,
                                    TagParser.create(NbtOps.INSTANCE).parseFully(text))
                            .result().map(component -> styled(component, selectedFont))
                            .orElseGet(() -> legacy(text, selectedFont));
                } catch (Exception ignoredSnbt) {
                    // Incomplete component input while editing falls back to visible literal text.
                }
            }
        }
        return legacy(text, selectedFont);
    }

    private static FormattedCharSequence styled(Component component, FontDescription font) {
        return component.copy().withStyle(style -> style.withFont(font)).getVisualOrderText();
    }

    private static FormattedCharSequence legacy(String text, FontDescription font) {
        return sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY.withFont(font), sink);
    }

    private static FontDescription font(String value) {
        Identifier id = Identifier.tryParse(value);
        return id == null ? FontDescription.DEFAULT : new FontDescription.Resource(id);
    }
}
